"""Workspace-scoped administration. No global admin or arbitrary workspace creation."""
import re
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .database import get_db
from .models import Membership, Workspace, ExternalIdentity
from .config import settings
from .security import current_user_id, require_permission, effective_permissions, lock_workspace, assert_administrator_remains

router = APIRouter(prefix="/v1/workspaces", tags=["Workspace administration"])
ALLOWED = frozenset({
    "forms.read", "forms.submissions.read", "forms.submissions.write",
    "sync.write", "workspace.manage"
})
SUBJECT = re.compile(r"^[a-zA-Z0-9_.:@-]{1,255}$")

class WorkspaceUpdate(BaseModel):
    name: str = Field(min_length=1, max_length=160)

class MembershipWrite(BaseModel):
    permissions: list[str] = Field(max_length=20)

def check_permissions(permissions: list[str]):
    if len(set(permissions)) != len(permissions) or not set(permissions).issubset(ALLOWED):
        raise HTTPException(422, "Unknown or duplicate permission")

def workspace_or_404(db: Session, workspace_id: str):
    workspace = db.get(Workspace, workspace_id)
    if workspace is None:
        raise HTTPException(404, "Workspace not found")
    return workspace

def admin(db: Session, workspace_id: str, user_id: str):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    return workspace_or_404(db, workspace_id)

def membership_out(item: Membership):
    return {"subject": item.subject, "permissions": item.permissions}

@router.get("/{workspace_id}/admin")
def get_workspace(workspace_id: str, user_id: str = Depends(current_user_id),
                  db: Session = Depends(get_db)):
    workspace = admin(db, workspace_id, user_id)
    return {"id": workspace.id, "name": workspace.name}

@router.patch("/{workspace_id}/admin")
def update_workspace(workspace_id: str, body: WorkspaceUpdate,
                     user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    admin(db, workspace_id, user_id)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    workspace = workspace_or_404(db, workspace_id)
    workspace.name = body.name.strip()
    if not workspace.name:
        raise HTTPException(422, "Workspace name required")
    db.commit()
    return {"id": workspace.id, "name": workspace.name}

@router.get("/{workspace_id}/members")
def list_members(workspace_id: str, user_id: str = Depends(current_user_id),
                 db: Session = Depends(get_db)):
    admin(db, workspace_id, user_id)
    members = db.scalars(select(Membership).where(Membership.workspace_id == workspace_id)
                         .order_by(Membership.subject)).all()
    return {"items": [membership_out(item) for item in members]}

@router.put("/{workspace_id}/members/{member_subject}")
def put_member(workspace_id: str, member_subject: str, body: MembershipWrite,
               user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    admin(db, workspace_id, user_id)
    if not SUBJECT.fullmatch(member_subject):
        raise HTTPException(422, "Invalid subject")
    check_permissions(body.permissions)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    if not set(body.permissions) <= effective_permissions(workspace_id, user_id, db):
        raise HTTPException(403, "Cannot grant permissions you do not hold")
    if db.scalar(select(Membership).where(Membership.workspace_id == workspace_id, Membership.subject == member_subject, Membership.user_id == user_id)) is not None:
        raise HTTPException(403, "Self-modification is disabled")
    item = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id, Membership.subject == member_subject))
    if item is None:
        identity = db.scalar(select(ExternalIdentity).where(
            ExternalIdentity.issuer == settings.oidc_issuer,
            ExternalIdentity.subject == member_subject))
        if identity is None:
            raise HTTPException(404, "Identity not registered; user must sign in first")
        item = Membership(workspace_id=workspace_id, subject=member_subject,
                          user_id=identity.user_id, permissions=list(body.permissions))
        db.add(item)
    else:
        item.permissions = list(body.permissions)
    assert_administrator_remains(db, workspace_id)
    db.commit()
    return membership_out(item)

@router.delete("/{workspace_id}/members/{member_subject}", status_code=204)
def delete_member(workspace_id: str, member_subject: str,
                  user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    admin(db, workspace_id, user_id)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    if db.scalar(select(Membership).where(Membership.workspace_id == workspace_id, Membership.subject == member_subject, Membership.user_id == user_id)) is not None:
        raise HTTPException(403, "Self-removal is disabled")
    item = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id, Membership.subject == member_subject))
    if item is None:
        raise HTTPException(404, "Membership not found")
    db.delete(item)
    assert_administrator_remains(db, workspace_id)
    db.commit()
