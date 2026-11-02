"""Opt-in PostgreSQL integration checks using temporary data and rollback."""
import os
from uuid import uuid4
import pytest
from fastapi import HTTPException
from sqlalchemy import select, text
from sqlalchemy.exc import OperationalError
from sqlalchemy.orm import Session
from app.database import engine
from app.models import Workspace, User, Membership
from app.security import lock_workspace, assert_administrator_remains

pytestmark = pytest.mark.skipif(os.getenv("FIELDHUB_TEST_POSTGRES") != "1", reason="Set FIELDHUB_TEST_POSTGRES=1")

@pytest.fixture
def pg_workspace():
    if engine.dialect.name != "postgresql":
        pytest.skip("PostgreSQL required")
    workspace_id = "sec-test-" + uuid4().hex[:20]
    user_id = str(uuid4())
    with Session(engine, expire_on_commit=False) as db:
        try:
            db.add(Workspace(id=workspace_id, name="Temporary test workspace"))
            db.add(User(id=user_id, display_name="Temporary test user"))
            db.flush()
            db.add(Membership(workspace_id=workspace_id, user_id=user_id,
                                  subject="test-" + uuid4().hex, permissions=["workspace.manage"]))
            db.commit()
            yield db, workspace_id, user_id
        finally:
            db.rollback()
            with engine.begin() as cleanup:
                cleanup.execute(text("DELETE FROM memberships WHERE workspace_id = :wid"), {"wid": workspace_id})
                cleanup.execute(text("DELETE FROM workspaces WHERE id = :wid"), {"wid": workspace_id})
                cleanup.execute(text("DELETE FROM users WHERE id = :uid"), {"uid": user_id})

def test_postgres_last_admin_protection(pg_workspace):
    db, workspace_id, user_id = pg_workspace
    lock_workspace(db, workspace_id)
    member = db.scalar(select(Membership).where(
        Membership.workspace_id == workspace_id, Membership.user_id == user_id))
    member.permissions = []
    with pytest.raises(HTTPException) as error:
        assert_administrator_remains(db, workspace_id)
    assert error.value.status_code == 409

def test_postgres_workspace_row_lock(pg_workspace):
    db, workspace_id, _ = pg_workspace
    lock_workspace(db, workspace_id)
    with engine.connect() as competing:
        with pytest.raises(OperationalError) as error:
            competing.execute(text("SELECT id FROM workspaces WHERE id = :wid FOR UPDATE NOWAIT"),
                              {"wid": workspace_id})
        assert error.value.orig.sqlstate == "55P03"
