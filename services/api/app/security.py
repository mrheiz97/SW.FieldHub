from functools import lru_cache
import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session
from .config import settings
from .database import get_db
from .models import Membership, ExternalIdentity, User, WorkspaceRole, WorkspaceRoleAssignment

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

def validate_oidc_token(token: str) -> str:
    try:
        key = jwks_client().get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token, key.key, algorithms=["RS256", "ES256"],
            audience=settings.oidc_audience, issuer=settings.oidc_issuer,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
        )
        subject = claims["sub"]
        if not isinstance(subject, str) or not subject:
            raise ValueError("Missing subject")
        return subject
    except (jwt.PyJWTError, ValueError) as exc:
        raise HTTPException(status_code=401, detail="Invalid access token") from exc

def current_subject(credentials: HTTPAuthorizationCredentials = Depends(bearer)) -> str:
    from .auth_providers import get_registry
    return get_registry().validate(credentials.credentials)

def current_user_id(subject: str = Depends(current_subject), db: Session = Depends(get_db)) -> str:
    """Resolve a verified external identity, provisioning a new unprivileged user if needed."""
    identity = db.scalar(select(ExternalIdentity).where(
        ExternalIdentity.issuer == settings.oidc_issuer,
        ExternalIdentity.subject == subject))
    if identity is not None:
        return identity.user_id
    # A new identity receives no memberships or permissions.
    from sqlalchemy.exc import IntegrityError
    from uuid import uuid4
    user_id = str(uuid4())
    try:
        db.add(User(id=user_id))
        # Flush parent first: there is no ORM relationship defining insert order.
        # Otherwise the external identity can violate its users(id) foreign key.
        db.flush()
        db.add(ExternalIdentity(user_id=user_id, issuer=settings.oidc_issuer, subject=subject))
        db.commit()
        return user_id
    except IntegrityError:
        db.rollback()
        identity = db.scalar(select(ExternalIdentity).where(
            ExternalIdentity.issuer == settings.oidc_issuer,
            ExternalIdentity.subject == subject))
        if identity is None:
            raise HTTPException(503, "Identity provisioning unavailable")
        return identity.user_id

def effective_permissions(workspace_id: str, user_id: str, db: Session) -> set[str]:
    membership = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id, Membership.user_id == user_id))
    if membership is None:
        return set()
    permissions = set(membership.permissions or [])
    roles = db.scalars(select(WorkspaceRole).join(
        WorkspaceRoleAssignment,
        (WorkspaceRoleAssignment.role_id == WorkspaceRole.id) &
        (WorkspaceRoleAssignment.workspace_id == WorkspaceRole.workspace_id)
    ).where(WorkspaceRole.workspace_id == workspace_id,
            WorkspaceRoleAssignment.user_id == user_id)).all()
    for role in roles:
        permissions.update(role.permissions or [])
    return permissions

def require_permission(workspace_id: str, permission: str, user_id: str, db: Session):
    if permission not in effective_permissions(workspace_id, user_id, db):
        raise HTTPException(status_code=403, detail="Workspace permission denied")
