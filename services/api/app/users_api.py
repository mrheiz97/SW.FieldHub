"""User profile endpoints using internal FieldHub identities."""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from .database import get_db
from .models import User
from .security import current_user_id

router = APIRouter(prefix="/v1/users", tags=["Users"])

class ProfileUpdate(BaseModel):
    displayName: str = Field(min_length=1, max_length=160)

def output(user: User):
    return {"id": user.id, "displayName": user.display_name or user.id}

@router.get("/me")
def get_me(user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    return output(db.get(User, user_id))

@router.patch("/me")
def update_me(body: ProfileUpdate, user_id: str = Depends(current_user_id), db: Session = Depends(get_db)):
    name = body.displayName.strip()
    if not name:
        raise HTTPException(422, "Display name required")
    user = db.get(User, user_id)
    user.display_name = name
    db.commit()
    return output(user)
