"""Read-only workspace UI schema with workspace-scoped authorization."""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from .database import get_db
from .security import current_user_id, effective_permissions, require_permission
from .workspace_modules import enabled_modules
from .ui_schema import build_ui

router = APIRouter(prefix="/v1/workspaces", tags=["Workspace UI"])


@router.get("/{workspace_id}/ui")
def get_workspace_ui(workspace_id: str, user_id: str = Depends(current_user_id),
                     db: Session = Depends(get_db)):
    # Any authenticated workspace member may read their own authorized UI.
    # No elevated permission is required, but membership is mandatory.
    from sqlalchemy import select
    from .models import Membership
    member = db.scalar(select(Membership.id).where(
        Membership.workspace_id == workspace_id, Membership.user_id == user_id))
    if member is None:
        from fastapi import HTTPException
        raise HTTPException(403, "Workspace membership required")
    return build_ui(effective_permissions(workspace_id, user_id, db),
                    enabled_modules(db, workspace_id))
