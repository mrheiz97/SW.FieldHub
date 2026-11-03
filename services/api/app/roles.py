"""Workspace-scoped, administrator-managed custom roles."""
from uuid import uuid4, UUID
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session
from .database import get_db
from .models import WorkspaceRole, WorkspaceRoleAssignment, Membership
from .security import current_user_id, require_permission, effective_permissions, lock_workspace, assert_administrator_remains
from .workspace_users import ALLOWED

router = APIRouter(prefix="/v1/workspaces/{workspace_id}", tags=["Workspace roles"])

class RoleBody(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    description: str = Field(default="", max_length=500)
    permissions: list[str] = Field(default_factory=list, max_length=20)

class RoleAssignments(BaseModel):
    roleIds: list[str] = Field(default_factory=list, max_length=30)

def uuid(value):
    try:
        return str(UUID(value))
    except (ValueError, TypeError, AttributeError):
        raise HTTPException(422, "Invalid UUID")

def output(role):
    return {"id": role.id, "workspaceId": role.workspace_id, "name": role.name,
            "description": role.description, "permissions": role.permissions}

def validate_grant(body, workspace_id, user_id, db):
    if len(body.permissions) != len(set(body.permissions)) or not set(body.permissions) <= ALLOWED:
        raise HTTPException(422, "Unknown or duplicate permission")
    if not set(body.permissions) <= effective_permissions(workspace_id, user_id, db):
        raise HTTPException(403, "Cannot grant permissions you do not hold")

@router.get("/roles")
def list_roles(workspace_id: str, user_id: str = Depends(current_user_id),
               db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    rows = db.scalars(select(WorkspaceRole).where(WorkspaceRole.workspace_id == workspace_id)
                      .order_by(WorkspaceRole.name)).all()
    return {"items": [output(x) for x in rows]}

@router.post("/roles", status_code=201)
def create_role(workspace_id: str, body: RoleBody, user_id: str = Depends(current_user_id),
                db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    validate_grant(body, workspace_id, user_id, db)
    name = body.name.strip()
    if not name:
        raise HTTPException(422, "Role name required")
    role = WorkspaceRole(id=str(uuid4()), workspace_id=workspace_id, name=name,
                         description=body.description.strip(), permissions=body.permissions)
    db.add(role)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Role name already exists")
    return output(role)

@router.put("/roles/{role_id}")
def update_role(workspace_id: str, role_id: str, body: RoleBody,
                user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    validate_grant(body, workspace_id, user_id, db)
    role = db.scalar(select(WorkspaceRole).where(WorkspaceRole.id == uuid(role_id),
                     WorkspaceRole.workspace_id == workspace_id).with_for_update())
    if role is None:
        raise HTTPException(404, "Role not found")
    # Avoid self-escalation by editing a role assigned to the caller.
    own = db.scalar(select(WorkspaceRoleAssignment).where(
        WorkspaceRoleAssignment.workspace_id == workspace_id,
        WorkspaceRoleAssignment.role_id == role.id,
        WorkspaceRoleAssignment.user_id == user_id))
    if own is not None and not set(body.permissions) <= set(role.permissions):
        raise HTTPException(403, "Cannot expand your own assigned role")
    name = body.name.strip()
    if not name:
        raise HTTPException(422, "Role name required")
    role.name = name
    role.description = body.description.strip()
    role.permissions = body.permissions
    assert_administrator_remains(db, workspace_id)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(409, "Role name already exists")
    return output(role)

@router.delete("/roles/{role_id}", status_code=204)
def delete_role(workspace_id: str, role_id: str, user_id: str = Depends(current_user_id),
                db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    role = db.scalar(select(WorkspaceRole).where(WorkspaceRole.id == uuid(role_id),
                     WorkspaceRole.workspace_id == workspace_id).with_for_update())
    if role is None:
        raise HTTPException(404, "Role not found")
    # Do not remove a role currently assigned to anyone; explicit unassignment first.
    assigned = db.scalar(select(WorkspaceRoleAssignment).where(
        WorkspaceRoleAssignment.role_id == role.id).limit(1))
    if assigned is not None:
        raise HTTPException(409, "Unassign role from all members before deleting")
    db.delete(role)
    db.commit()

@router.get("/users/{target_id}/roles")
def user_roles(workspace_id: str, target_id: str, user_id: str = Depends(current_user_id),
               db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    target_id = uuid(target_id)
    if db.scalar(select(Membership).where(Membership.workspace_id == workspace_id,
                                         Membership.user_id == target_id)) is None:
        raise HTTPException(404, "Member not found")
    rows = db.scalars(select(WorkspaceRoleAssignment).where(
        WorkspaceRoleAssignment.workspace_id == workspace_id,
        WorkspaceRoleAssignment.user_id == target_id)).all()
    return {"roleIds": [x.role_id for x in rows]}

@router.put("/users/{target_id}/roles")
def assign_roles(workspace_id: str, target_id: str, body: RoleAssignments,
                 user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    target_id = uuid(target_id)
    if target_id == user_id:
        raise HTTPException(403, "Self-modification is disabled")
    if db.scalar(select(Membership).where(Membership.workspace_id == workspace_id,
                                         Membership.user_id == target_id)) is None:
        raise HTTPException(404, "Member not found")
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    # The target could have been removed while this request waited for the lock.
    target_member = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id,
        Membership.user_id == target_id).with_for_update())
    if target_member is None:
        raise HTTPException(404, "Member not found")
    ids = [uuid(x) for x in body.roleIds]
    if len(ids) != len(set(ids)):
        raise HTTPException(422, "Duplicate roles")
    roles = db.scalars(select(WorkspaceRole).where(
        WorkspaceRole.workspace_id == workspace_id, WorkspaceRole.id.in_(ids))).all()
    if len(roles) != len(ids):
        raise HTTPException(422, "Role does not belong to workspace")
    allowed = effective_permissions(workspace_id, user_id, db)
    if any(not set(role.permissions) <= allowed for role in roles):
        raise HTTPException(403, "Cannot assign roles with permissions you do not hold")
    old = db.scalars(select(WorkspaceRoleAssignment).where(
        WorkspaceRoleAssignment.workspace_id == workspace_id,
        WorkspaceRoleAssignment.user_id == target_id)).all()
    for item in old:
        db.delete(item)
    db.flush()
    for role_id in ids:
        db.add(WorkspaceRoleAssignment(workspace_id=workspace_id, role_id=role_id,
                                       user_id=target_id))
    assert_administrator_remains(db, workspace_id)
    db.commit()
    return {"roleIds": ids}
