"""FieldHub same-origin BFF, server-side session management."""
import os
import secrets
import hashlib
import base64
import time
import json
import jwt
from jwt import PyJWKClient
from urllib.parse import urlencode
import httpx
import redis.asyncio as redis
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import RedirectResponse, Response

app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
store=redis.from_url(os.environ.get("BFF_REDIS_URL","redis://web-session:6379/0"),decode_responses=True)
issuer=os.environ["BFF_OIDC_ISSUER"].rstrip("/")
token_url=os.environ["BFF_OIDC_TOKEN_URL"]
redirect_uri=os.environ["BFF_REDIRECT_URI"]
client_id=os.environ.get("BFF_OIDC_CLIENT_ID","fieldhub-web-dev")
api_url=os.environ.get("BFF_API_URL","http://api:8000").rstrip("/")
jwks_url=os.environ.get("BFF_OIDC_JWKS_URL",issuer+"/protocol/openid-connect/certs")
jwks_client=PyJWKClient(jwks_url,cache_jwk_set=True,lifespan=300)
MAX_SESSION_AGE=8*3600
secure=os.environ.get("BFF_SECURE_COOKIES","true")=="true"
cookie_name="__Host-fh_session" if secure else "fh_dev_session"
from urllib.parse import urlparse
if not secure and (urlparse(redirect_uri).hostname not in ("127.0.0.1","localhost") or urlparse(redirect_uri).scheme!="http"):
    raise RuntimeError("Insecure BFF cookies are permitted only for localhost development")
if secure and urlparse(redirect_uri).scheme!="https":
    raise RuntimeError("Production BFF requires HTTPS callback")


def set_cookie(response,sid):
    response.set_cookie(cookie_name,sid,httponly=True,secure=secure,samesite="lax",path="/",max_age=MAX_SESSION_AGE)

async def session(request):
    sid=request.cookies.get(cookie_name)
    if not sid:return None,None
    data=await store.hgetall("session:"+sid)
    if not data:return sid,None
    if int(data.get("created","0"))+MAX_SESSION_AGE<int(time.time()):
        await store.delete("session:"+sid)
        return sid,None
    return sid,data

def verify_id_token(token,nonce):
    key=jwks_client.get_signing_key_from_jwt(token)
    claims=jwt.decode(token,key.key,algorithms=["RS256"],audience=client_id,issuer=issuer,options={"require":["exp","iat","iss","aud","sub"]})
    if not secrets.compare_digest(str(claims.get("nonce","")),nonce):
        raise ValueError("OIDC nonce mismatch")
    return claims

@app.get("/auth/login")
async def login():
    state=secrets.token_urlsafe(32)
    verifier=secrets.token_urlsafe(64)
    nonce=secrets.token_urlsafe(32)
    digest=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    await store.setex("state:"+state,300,json.dumps({"verifier":verifier,"nonce":nonce}))
    params=urlencode(dict(client_id=client_id,response_type="code",scope="openid",redirect_uri=redirect_uri,state=state,nonce=nonce,code_challenge=digest,code_challenge_method="S256"))
    result=RedirectResponse(issuer+"/protocol/openid-connect/auth?"+params,status_code=303)
    result.set_cookie("fh_login_state",state,httponly=True,secure=secure,samesite="lax",path="/auth",max_age=300)
    result.headers["Cache-Control"]="no-store"
    return result

@app.get("/auth/callback")
async def callback(request:Request,code:str,state:str):
    browser_state=request.cookies.get("fh_login_state","")
    if not browser_state or not secrets.compare_digest(browser_state,state):
        raise HTTPException(400,"Login state mismatch")
    stored=await store.getdel("state:"+state)
    if not stored:raise HTTPException(400,"Expired login state")
    details=json.loads(stored)
    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response=await client.post(token_url,data=dict(grant_type="authorization_code",client_id=client_id,code=code,code_verifier=details["verifier"],redirect_uri=redirect_uri))
        if response.status_code!=200:raise HTTPException(502,"Login failed")
        payload=response.json()
        verify_id_token(payload["id_token"],details["nonce"])
        access=payload["access_token"]
        refresh=payload.get("refresh_token","")
        ttl=max(1,int(payload.get("expires_in",300)))
    except (httpx.HTTPError,KeyError,ValueError,jwt.PyJWTError) as exc:
        raise HTTPException(502,"OIDC token verification failed") from exc
    sid=secrets.token_urlsafe(32)
    csrf=secrets.token_urlsafe(32)
    now=int(time.time())
    await store.hset("session:"+sid,mapping={"access":access,"refresh":refresh,"expires":now+ttl,"created":now,"csrf":csrf})
    await store.expire("session:"+sid,MAX_SESSION_AGE)
    result=RedirectResponse("/",status_code=303)
    result.delete_cookie("fh_login_state",path="/auth",secure=secure,httponly=True,samesite="lax")
    set_cookie(result,sid)
    result.headers["Cache-Control"]="no-store"
    return result

async def valid_access(sid,data):
    if int(data.get("expires","0"))>int(time.time())+30:
        return data["access"]
    lock=store.lock("refresh-lock:"+sid,timeout=15,blocking_timeout=8)
    try:
        async with lock:
            latest=await store.hgetall("session:"+sid)
            if not latest:raise HTTPException(401,"Session expired")
            if int(latest.get("expires","0"))>int(time.time())+30:return latest["access"]
            refresh=latest.get("refresh")
            if not refresh:
                await store.delete("session:"+sid)
                raise HTTPException(401,"Session expired")
            try:
                async with httpx.AsyncClient(timeout=10) as client:
                    response=await client.post(token_url,data={"grant_type":"refresh_token","client_id":client_id,"refresh_token":refresh})
                if response.status_code!=200:raise ValueError("Refresh rejected")
                payload=response.json()
                access=payload["access_token"]
                new_refresh=payload.get("refresh_token",refresh)
                ttl=max(1,int(payload.get("expires_in",300)))
            except (httpx.HTTPError,ValueError,KeyError) as exc:
                await store.delete("session:"+sid)
                raise HTTPException(401,"Session expired") from exc
            await store.hset("session:"+sid,mapping={"access":access,"refresh":new_refresh,"expires":int(time.time())+ttl})
            return access
    except redis.exceptions.LockError as exc:
        raise HTTPException(503,"Session temporarily busy") from exc

@app.get("/auth/session")
async def get_session(request:Request):
    sid,data=await session(request)
    if not data:raise HTTPException(401,"Login required")
    await valid_access(sid,data)
    return {"authenticated":True,"csrf":data["csrf"]

def require_origin(request):
    expected=str(request.base_url).rstrip("/")
    if request.headers.get("origin")!=expected:
        raise HTTPException(403,"Origin mismatch")

@app.post("/auth/logout")
async def logout(request:Request):
    require_origin(request)
    sid,data=await session(request)
    if not data:raise HTTPException(401,"Login required")
    if not secrets.compare_digest(request.headers.get("x-csrf-token",""),data["csrf"]):
        raise HTTPException(403,"Invalid CSRF token")
    await store.delete("session:"+sid)
    result=Response(status_code=204)
    result.delete_cookie(cookie_name,path="/",secure=secure,httponly=True,samesite="lax")
    return result

@app.api_route("/v1/{path:path}",methods=["GET","POST","PUT","PATCH","DELETE"])
async def proxy(path:str,request:Request):
    sid,data=await session(request)
    if not data:raise HTTPException(401,"Login required")
    if ".." in path.split("/") or len(path)>2048:raise HTTPException(400,"Invalid path")
    if request.method!="GET":
        require_origin(request)
        if not secrets.compare_digest(request.headers.get("x-csrf-token",""),data["csrf"]):
            raise HTTPException(403,"Invalid CSRF token")
    body=await request.body()
    if len(body)>1048576:raise HTTPException(413,"Body too large")
    access=await valid_access(sid,data)
    headers={"Authorization":"Bearer "+access,"Accept":"application/json"}
    if request.headers.get("content-type","").startswith("application/json"):
        headers["Content-Type"]="application/json"
    try:
        async with httpx.AsyncClient(timeout=15,follow_redirects=False) as client:
            upstream=await client.request(request.method,api_url+"/v1/"+path,
                                          params=request.query_params,content=body,headers=headers)
    except httpx.RequestError:
        raise HTTPException(502,"API unavailable")
    if upstream.status_code==401:
        await store.delete("session:"+sid)
    return Response(upstream.content,status_code=upstream.status_code,
                    media_type=upstream.headers.get("content-type","application/json"),
                    headers={"Cache-Control":"no-store"})
