"""FieldHub same-origin BFF, server-side session management."""
import os
import secrets
import hashlib
import base64
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
secure=os.environ.get("BFF_SECURE_COOKIES","true")=="true"
cookie_name="__Host-fh_session" if secure else "fh_dev_session"
from urllib.parse import urlparse
if not secure and (urlparse(redirect_uri).hostname not in ("127.0.0.1","localhost") or urlparse(redirect_uri).scheme!="http"):
    raise RuntimeError("Insecure BFF cookies are permitted only for localhost development")
if secure and urlparse(redirect_uri).scheme!="https":
    raise RuntimeError("Production BFF requires HTTPS callback")


def set_cookie(response,sid):
    response.set_cookie(cookie_name,sid,httponly=True,secure=secure,samesite="lax",path="/",max_age=3600)

async def session(request):
    sid=request.cookies.get(cookie_name)
    if not sid:return None,None
    data=await store.hgetall("session:"+sid)
    return sid,data or None

@app.get("/auth/login")
async def login():
    state=secrets.token_urlsafe(32)
    verifier=secrets.token_urlsafe(64)
    digest=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    await store.setex("state:"+state,300,verifier)
    params=urlencode(dict(client_id=client_id,response_type="code",scope="openid",redirect_uri=redirect_uri,state=state,code_challenge=digest,code_challenge_method="S256"))
    return RedirectResponse(issuer+"/protocol/openid-connect/auth?"+params,status_code=303)

@app.get("/auth/callback")
async def callback(code:str,state:str):
    verifier=await store.getdel("state:"+state)
    if not verifier:raise HTTPException(400,"Invalid login state")
    async with httpx.AsyncClient(timeout=10) as client:
        response=await client.post(token_url,data=dict(grant_type="authorization_code",client_id=client_id,code=code,code_verifier=verifier,redirect_uri=redirect_uri))
    if response.status_code!=200:raise HTTPException(502,"Login failed")
    payload=response.json()
    sid=secrets.token_urlsafe(32)
    csrf=secrets.token_urlsafe(32)
    await store.hset("session:"+sid,mapping=dict(access=payload["access_token"],csrf=csrf))
    await store.expire("session:"+sid,min(3600,max(30,int(payload.get("expires_in",300))-30)))
    result=RedirectResponse("/",status_code=303)
    set_cookie(result,sid)
    result.headers["Cache-Control"]="no-store"
    return result

@app.get("/auth/session")
async def get_session(request:Request):
    sid,data=await session(request)
    if not data:raise HTTPException(401,"Login required")
    return {"authenticated":True,"csrf":data["csrf"]}

def require_origin(request):
    expected=str(request.base_url).rstrip("/")
    if request.headers.get("origin")!=expected:
        raise HTTPException(403,"Origin mismatch")

@app.post("/auth/logout")
async def logout(request:Request):
    require_origin(request)
    sid,data=await session(request)
    if not data:raise HTTPException(401,"Login required")
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
    headers={"Authorization":"Bearer "+data["access"],"Accept":"application/json"}
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
