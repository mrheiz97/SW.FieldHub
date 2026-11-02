"""Workspace-scoped module activation. UI visibility does not replace API authorization."""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.orm import Session

from .database import get_db
from .models import WorkspaceModule
from .module_registry import MODULES
from .security import current_user_id, require_permission, lock_workspace

router = APIRouter(prefix="/v1/workspaces", tags=["Workspace modules"])
KNOWN_MODULES = frozenset(module.id for module in MODULES)


class ModuleUpdate(BaseModel):
    enabled: bool


def enabled_modules(db: Session, workspace_id: str) -> set[str]:
    """Absent settings are enabled to preserve existing workspace behavior."""
    rows = db.scalars(select(WorkspaceModule).where(WorkspaceModule.workspace_id == workspace_id)).all()
    return KNOWN_MODULES - {row.module_id for row in rows if not row.enabled}


@router.get("/{workspace_id}/modules")
def list_modules(workspace_id: str, user_id: str = Depends(current_user_id),
                 db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    enabled = enabled_modules(db, workspace_id)
    return {"items": [{"id": module.id, "version": module.version,
                       "label": module.label, "enabled": module.id in enabled}
                      for module in MODULES]}


@router.put("/{workspace_id}/modules/{module_id}")
def set_module(workspace_id: str, module_id: str, body: ModuleUpdate,
               user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "workspace.manage", user_id, db)
    if module_id not in KNOWN_MODULES:
        raise HTTPException(404, "Unknown module")
    lock_workspace(db, workspace_id)
    require_permission(workspace_id, "workspace.manage", user_id, db)
    row = db.scalar(select(WorkspaceModule).where(
        WorkspaceModule.workspace_id == workspace_id,
        WorkspaceModule.module_id == module_id).with_for_update())
    if row is None:
        row = WorkspaceModule(workspace_id=workspace_id, module_id=module_id, enabled=body.enabled)
        db.add(row)
    else:
        row.enabled = body.enabled
    db.commit()
    return {"id": module_id, "enabled": body.enabled}
