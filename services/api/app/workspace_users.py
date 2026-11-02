"""Workspace member administration by internal FieldHub user ID."""
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .database import get_db
from .models import Membership, User
from .security import current_user_id, require_permission, effective_permissions, lock_workspace, assert_administrator_remains

router = APIRouter(prefix="/v1/workspaces", tags=["Workspace users"])
ALLOWED = frozenset({"forms.read", "forms.submissions.read", "forms.submissions.write", "sync.write", "workspace.manage"})

class PermissionsUpdate(BaseModel):
    permissions: list[str] = Field(max_length=20)

def valid_uuid(value: str) -> str:
    try:
        return str(UUID(value))
    except (ValueError, AttributeError):
        raise HTTPException(422, "Invalid FieldHub user ID")

def member_out(member: Membership, user: User):
    return {"userId": user.id, "displayName": user.display_name or user.id, "permissions": member.permissions}

@router.get("/{workspace_id}/users")
def list_users(workspace_id: str, user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    rows = db.execute(select(Membership, User).join(User, Membership.user_id == User.id)
                      .where(Membership.workspace_id == workspace_id).order_by(User.id)).all()
    return {"items": [member_out(member, user) for member, user in rows]}

@router.put("/{workspace_id}/users/{target_id}")
def update_user(workspace_id: str, target_id: str, body: PermissionsUpdate,
                user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    target_id = valid_uuid(target_id)
    if target_id == user_id:
        raise HTTPException(403, "Self-modification is disabled")
    if len(set(body.permissions)) != len(body.permissions) or not set(body.permissions) <= ALLOWED:
        raise HTTPException(422, "Unknown or duplicate permission")
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    member = db.scalar(select(Membership).where(Membership.workspace_id == workspace_id,
                                                Membership.user_id == target_id))
    if member is None:
        raise HTTPException(404, "Member not found")
    if not set(body.permissions) <= effective_permissions(workspace_id, user_id, db):
        raise HTTPException(403, "Cannot grant permissions you do not hold")
    member.permissions = list(body.permissions)
    assert_administrator_remains(db, workspace_id)
    db.commit()
    return member_out(member, db.get(User, target_id))

@router.delete("/{workspace_id}/users/{target_id}", status_code=204)
def remove_user(workspace_id: str, target_id: str, user_id: str = Depends(current_user_id),
                db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    target_id = valid_uuid(target_id)
    if target_id == user_id:
        raise HTTPException(403, "Self-removal is disabled")
    member = db.scalar(select(Membership).where(Membership.workspace_id == workspace_id,
                                                Membership.user_id == target_id))
    if member is None:
        raise HTTPException(404, "Member not found")
    db.delete(member)
    assert_administrator_remains(db, workspace_id)
    db.commit()
