from functools import lru_cache
import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session
from .config import settings
from .database import get_db
from .models import Membership

bearer = HTTPBearer(auto_error=True)

@lru_cache(maxsize=1)
def jwks_client():
    from urllib.parse import urlparse
    url = urlparse(settings.oidc_jwks_url)
    secure = url.scheme == "https" and bool(url.hostname)
    local_dev = (settings.local_dev_http_jwks and url.scheme == "http"
                 and url.hostname == "keycloak" and url.port == 8080
                 and url.path == "/realms/fieldhub/protocol/openid-connect/certs")
    if not (secure or local_dev):
        raise RuntimeError("JWKS requires HTTPS (except explicitly enabled local Keycloak development)")
    return jwt.PyJWKClient(settings.oidc_jwks_url)

def current_subject(credentials: HTTPAuthorizationCredentials = Depends(bearer)) -> str:
    try:
        key = jwks_client().get_signing_key_from_jwt(credentials.credentials)
        claims = jwt.decode(
            credentials.credentials, key.key, algorithms=["RS256", "ES256"],
            audience=settings.oidc_audience, issuer=settings.oidc_issuer,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
        )
        subject = claims["sub"]
        if not isinstance(subject, str) or not subject:
            raise ValueError("Missing subject")
        return subject
    except (jwt.PyJWTError, ValueError) as exc:
        raise HTTPException(status_code=401, detail="Invalid access token") from exc

def require_permission(workspace_id: str, permission: str, subject: str, db: Session):
    membership = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id, Membership.subject == subject
    ))
    if membership is None or permission not in membership.permissions:
        raise HTTPException(status_code=403, detail="Workspace permission denied")
