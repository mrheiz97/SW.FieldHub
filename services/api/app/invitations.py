"""Single-use workspace invitations; never grant access based on an email address."""
from datetime import datetime, timedelta, timezone
from hashlib import sha256
from secrets import token_urlsafe
from uuid import uuid4
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .config import settings
from .database import get_db
from .models import ExternalIdentity, Membership, WorkspaceInvitation
from .security import current_user_id, require_permission
from .workspace_users import ALLOWED

router = APIRouter(prefix="/v1", tags=["Invitations"])

class InvitationCreate(BaseModel):
    permissions: list[str] = Field(min_length=1, max_length=20)
    expiresInHours: int = Field(default=48, ge=1, le=168)

class InvitationClaim(BaseModel):
    token: str = Field(min_length=32, max_length=256)

def now():
    return datetime.now(timezone.utc)

def invitation_out(inv):
    return {"id": inv.id, "workspaceId": inv.workspace_id, "permissions": inv.permissions,
            "expiresAt": inv.expires_at.isoformat(), "claimed": inv.claimed_at is not None,
            "revoked": inv.revoked_at is not None}

@router.post("/workspaces/{workspace_id}/invitations", status_code=201)
def create_invitation(workspace_id: str, body: InvitationCreate,
                      user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    if len(body.permissions) != len(set(body.permissions)) or not set(body.permissions) <= ALLOWED:
        raise HTTPException(422, "Unknown or duplicate permission")
    # An invitation cannot grant a permission the issuing administrator lacks.
    admin = db.scalar(select(Membership).where(Membership.workspace_id == workspace_id,
                                               Membership.user_id == user_id))
    if not set(body.permissions) <= set(admin.permissions):
        raise HTTPException(403, "Cannot invite with permissions you do not hold")
    token = token_urlsafe(32)
    inv = WorkspaceInvitation(id=str(uuid4()), workspace_id=workspace_id,
          token_hash=sha256(token.encode()).hexdigest(), permissions=list(body.permissions),
          created_by=user_id, expires_at=now()+timedelta(hours=body.expiresInHours))
    db.add(inv)
    db.commit()
    # The raw secret is returned exactly once and never stored in plaintext.
    return {**invitation_out(inv), "token": token}

@router.get("/workspaces/{workspace_id}/invitations")
def list_invitations(workspace_id: str, user_id: str = Depends(current_user_id),
                     db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    invitations = db.scalars(select(WorkspaceInvitation)
                             .where(WorkspaceInvitation.workspace_id == workspace_id)
                             .order_by(WorkspaceInvitation.created_at.desc()).limit(100)).all()
    return {"items": [invitation_out(inv) for inv in invitations]}

@router.delete("/workspaces/{workspace_id}/invitations/{invitation_id}", status_code=204)
def revoke_invitation(workspace_id: str, invitation_id: str,
                      user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    inv = db.scalar(select(WorkspaceInvitation).where(
        WorkspaceInvitation.id == invitation_id, WorkspaceInvitation.workspace_id == workspace_id).with_for_update())
    if inv is None:
        raise HTTPException(404, "Invitation not found")
    if inv.claimed_at is not None:
        raise HTTPException(409, "Invitation already claimed")
    inv.revoked_at = now()
    db.commit()

@router.post("/invitations/claim")
def claim_invitation(body: InvitationClaim, user_id: str = Depends(current_user_id),
                     db: Session = Depends(get_db)):
    digest = sha256(body.token.encode()).hexdigest()
    inv = db.scalar(select(WorkspaceInvitation).where(
        WorkspaceInvitation.token_hash == digest).with_for_update())
    if inv is None or inv.claimed_at is not None or inv.revoked_at is not None or inv.expires_at <= now():
        raise HTTPException(400, "Invitation invalid or expired")
    if db.scalar(select(Membership).where(Membership.workspace_id == inv.workspace_id,
                                         Membership.user_id == user_id)) is not None:
        raise HTTPException(409, "Already a workspace member")
    # Legacy subject is still NOT NULL in v0.22 schema. Resolve only the
    # verified issuer mapping for the authenticated internal user.
    identity = db.scalar(select(ExternalIdentity).where(
        ExternalIdentity.user_id == user_id, ExternalIdentity.issuer == settings.oidc_issuer)
        .order_by(ExternalIdentity.id))
    if identity is None:
        raise HTTPException(409, "No verified identity for configured provider")
    db.add(Membership(workspace_id=inv.workspace_id, user_id=user_id,
                      subject=identity.subject, permissions=list(inv.permissions)))
    inv.claimed_by = user_id
    inv.claimed_at = now()
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Membership already exists")
    return {"workspaceId": inv.workspace_id, "permissions": inv.permissions}
