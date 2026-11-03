"""Workspace-scoped recruiting contacts, initially owned by their creator."""
from datetime import datetime, timezone
from uuid import uuid4
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy import select
from sqlalchemy.orm import Session
from .database import get_db
from .models import RecruitingContact
from .security import current_user_id, effective_permissions

router = APIRouter(prefix="/v1/workspaces/{workspace_id}/recruiting", tags=["Recruiting"])
STATES = {"new", "contacted", "interested", "converted", "declined"}

class ContactWrite(BaseModel):
    name: str = Field(default="", max_length=160)
    firstName: str = Field(default="", max_length=80)
    lastName: str = Field(default="", max_length=80)
    phone: str | None = Field(default=None, max_length=80)
    email: str | None = Field(default=None, max_length=254)
    status: str = "new"
    notes: str = Field(default="", max_length=5000)
    followUpAt: datetime | None = None

def require_recruiting_permission(workspace_id, permission, user_id, db):
    from fastapi import HTTPException
    permissions = effective_permissions(workspace_id, user_id, db)
    if permission not in permissions and "workspace.manage" not in permissions:
        raise HTTPException(403, "Recruiting permission denied")

def serialize(row):
    return {"id": row.id, "workspaceId": row.workspace_id, "ownerId": row.owner_id,
            "name": row.name, "firstName": row.first_name, "lastName": row.last_name,
            "phone": row.phone, "email": row.email, "status": row.status, "notes": row.notes,
            "followUpAt": row.follow_up_at.isoformat() if row.follow_up_at else None}

def validate(body):
    if not (body.name.strip() or body.firstName.strip() or body.lastName.strip()) or body.status not in STATES:
        raise HTTPException(422, "Invalid name or status")
    if body.email and ("@" not in body.email or len(body.email.split("@")[-1]) < 3):
        raise HTTPException(422, "Invalid email")
    if body.followUpAt is not None and body.followUpAt.tzinfo is None:
        raise HTTPException(422, "followUpAt requires timezone")

@router.get("/contacts")
def list_contacts(workspace_id: str, user_id: str = Depends(current_user_id),
                  db: Session = Depends(get_db)):
    require_recruiting_permission(workspace_id, "recruiting.read", user_id, db)
    rows = db.scalars(select(RecruitingContact).where(
        RecruitingContact.workspace_id == workspace_id,
        RecruitingContact.owner_id == user_id).order_by(
        RecruitingContact.id.desc()).limit(100)).all()
    return {"items": [serialize(row) for row in rows]}

@router.post("/contacts", status_code=201)
def create_contact(workspace_id: str, body: ContactWrite,
                   user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_recruiting_permission(workspace_id, "recruiting.write", user_id, db)
    validate(body)
    row = RecruitingContact(id=str(uuid4()), workspace_id=workspace_id, owner_id=user_id,
                            name=(body.name.strip() or (body.firstName.strip() + " " + body.lastName.strip()).strip()),
                            first_name=body.firstName.strip(), last_name=body.lastName.strip(),
                            phone=body.phone.strip() if body.phone else None,
                            email=body.email.strip() if body.email else None,
                            status=body.status, notes=body.notes,
                            follow_up_at=body.followUpAt)
    db.add(row)
    db.commit()
    return serialize(row)

@router.put("/contacts/{contact_id}")
def update_contact(workspace_id: str, contact_id: str, body: ContactWrite,
                   user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    require_recruiting_permission(workspace_id, "recruiting.write", user_id, db)
    validate(body)
    row = db.scalar(select(RecruitingContact).where(
        RecruitingContact.id == contact_id, RecruitingContact.workspace_id == workspace_id,
        RecruitingContact.owner_id == user_id).with_for_update())
    if row is None:
        raise HTTPException(404, "Contact not found")
    row.name = (body.name.strip() or (body.firstName.strip() + " " + body.lastName.strip()).strip())
    row.first_name, row.last_name = body.firstName.strip(), body.lastName.strip()
    row.phone = body.phone.strip() if body.phone else None
    row.email = body.email.strip() if body.email else None
    row.status, row.notes = body.status, body.notes
    row.follow_up_at = body.followUpAt
    db.commit()
    return serialize(row)
