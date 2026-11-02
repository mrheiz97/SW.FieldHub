"""Explicitly opt-in localhost-only OIDC PKCE diagnostic UI. Never deploy publicly."""
from pathlib import Path
from urllib.parse import urlparse
import httpx
from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field
from .config import settings

router = APIRouter(prefix="/dev", include_in_schema=False)
REDIRECT = "http://127.0.0.1:8000/dev/login"
TOKEN_URL = "http://keycloak:8080/realms/fieldhub/protocol/openid-connect/token"

def require_local(request: Request):
    if not settings.local_dev_login:
        raise HTTPException(404, "Not found")
    # Localhost binding in Compose is mandatory as additional protection.
    host = request.headers.get("host", "").split(":")[0]
    if host not in ("127.0.0.1", "localhost"):
        raise HTTPException(403, "Local development only")

@router.get("/login")
def login(request: Request):
    require_local(request)
    return FileResponse(Path(__file__).with_name("dev_login.html"), media_type="text/html",
                        headers={"Cache-Control": "no-store", "Content-Security-Policy":
                        "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'; referrer no-referrer"})

class CodeExchange(BaseModel):
    code: str = Field(min_length=5, max_length=4096)
    verifier: str = Field(min_length=43, max_length=128)

@router.post("/token")
async def exchange(request: Request, body: CodeExchange):
    require_local(request)
    if not (settings.local_dev_http_jwks and settings.oidc_issuer == "http://localhost:8080/realms/fieldhub"):
        raise HTTPException(403, "Local Keycloak configuration required")
    try:
        async with httpx.AsyncClient(timeout=8.0) as client:
            result = await client.post(TOKEN_URL, data={
                "grant_type": "authorization_code", "client_id": "fieldhub-web-dev",
                "code": body.code, "code_verifier": body.verifier,
                "redirect_uri": REDIRECT
            })
    except httpx.RequestError:
        raise HTTPException(502, "Keycloak unreachable")
    if result.status_code != 200:
        raise HTTPException(400, "Code exchange rejected by Keycloak")
    data = result.json()
    return {"access_token": data["access_token"], "id_token": data.get("id_token"), "expires_in": data.get("expires_in", 0)}
