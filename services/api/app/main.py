from uuid import uuid4
from datetime import datetime, timezone
from fastapi import FastAPI, Depends, HTTPException, Header
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .database import get_db
from .models import Workspace, Membership, Form, Submission, User
from .security import current_user_id, require_permission, effective_permissions
from .module_registry import visible_modules

from .dev_login import router as dev_login_router
from .workspace_admin import router as workspace_admin_router
from .users_api import router as users_router
from .workspace_users import router as workspace_users_router
from .invitations import router as invitations_router
from .roles import router as roles_router

app = FastAPI(title="StumpfWorks FieldHub API", version="0.27.0")

app.include_router(dev_login_router)
app.include_router(workspace_admin_router)
app.include_router(users_router)
app.include_router(workspace_users_router)
app.include_router(invitations_router)
app.include_router(roles_router)

class SubmissionWrite(BaseModel):
    formVersion: int = Field(ge=1)
    values: dict[str, str] = Field(max_length=30)

def output_submission(item: Submission):
    return dict(id=item.id, workspaceId=item.workspace_id, formId=item.form_id,
                formVersion=item.form_version, values=item.values, revision=str(item.revision),
                createdAt=item.created_at.isoformat(), updatedAt=item.updated_at.isoformat())

@app.get("/health")
def health():
    return {"status": "ok"}

@app.get("/v1/bootstrap")
def bootstrap(user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    memberships = db.scalars(select(Membership).where(Membership.user_id == user_id)).all()
    ids = [m.workspace_id for m in memberships]
    names = {w.id: w.name for w in db.scalars(select(Workspace).where(Workspace.id.in_(ids))).all()}
    user = db.get(User, user_id)
    return {"schemaVersion": 1, "user": {"id": user_id, "displayName": user.display_name or user_id},
            "workspaces": [{"id": m.workspace_id, "name": names.get(m.workspace_id, m.workspace_id),
                            "permissions": sorted(effective_permissions(m.workspace_id, user_id, db)), "navigation": []} for m in memberships]}

@app.get("/v1/workspaces/{workspace_id}/forms")
def list_forms(workspace_id: str, user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "forms.read", user_id, db)
    forms = db.scalars(select(Form).where(Form.workspace_id == workspace_id, Form.published.is_(True))
                       .order_by(Form.form_id, Form.version.desc())).all()
    latest = {}
    for form in forms:
        latest.setdefault(form.form_id, {"id": form.form_id, "title": form.title, "version": form.version})
    return {"items": list(latest.values())}

@app.get("/v1/workspaces/{workspace_id}/forms/{form_id}")
def get_form(workspace_id: str, form_id: str, user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "forms.read", user_id, db)
    form = db.scalar(select(Form).where(Form.workspace_id == workspace_id, Form.form_id == form_id,
                                      Form.published.is_(True)).order_by(Form.version.desc()).limit(1))
    if form is None:
        raise HTTPException(404, "Form not found")
    return form.definition

@app.get("/v1/workspaces/{workspace_id}/forms/{form_id}/submissions")
def list_submissions(workspace_id: str, form_id: str, limit: int = 25,
                     user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "forms.submissions.read", user_id, db)
    if not 1 <= limit <= 100:
        raise HTTPException(422, "Invalid limit")
    rows = db.scalars(select(Submission).where(Submission.workspace_id == workspace_id,
                      Submission.form_id == form_id).order_by(Submission.created_at.desc()).limit(limit)).all()
    return {"items": [output_submission(x) for x in rows], "nextCursor": None}

@app.post("/v1/workspaces/{workspace_id}/forms/{form_id}/submissions", status_code=201)
def create_submission(workspace_id: str, form_id: str, body: SubmissionWrite,
                      idempotency_key: str = Header(min_length=8, max_length=128),
                      user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "forms.submissions.write", user_id, db)
    form = db.scalar(select(Form).where(Form.workspace_id == workspace_id, Form.form_id == form_id,
                       Form.version == body.formVersion, Form.published.is_(True)))
    if form is None:
        raise HTTPException(404, "Published form version not found")
    # Do not accept submissions until field-by-field server validation and persistent idempotency are implemented.
    raise HTTPException(501, "Submission writes are not enabled yet")

@app.patch("/v1/workspaces/{workspace_id}/forms/{form_id}/submissions/{submission_id}")
def update_submission(workspace_id: str, form_id: str, submission_id: str,
                      body: SubmissionWrite, if_match: str = Header(),
                      user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "forms.submissions.write", user_id, db)
    raise HTTPException(501, "Submission updates are not enabled yet")

@app.post("/v1/workspaces/{workspace_id}/sync")
def sync_workspace(workspace_id: str, user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_permission(workspace_id, "sync.write", user_id, db)
    raise HTTPException(501, "Sync is not enabled yet")
