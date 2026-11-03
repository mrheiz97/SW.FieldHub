"""Recruiting contact isolation and validation regression tests."""
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.database import Base, get_db
from app.main import app
from app.models import Membership, User, Workspace
from app.security import current_user_id

@pytest.fixture
def ctx():
    engine = create_engine("sqlite+pysqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    owner, stranger = str(uuid4()), str(uuid4())
    with factory() as db:
        db.add(Workspace(id="ravens", name="Ravens"))
        db.add_all([User(id=owner), User(id=stranger)])
        db.flush()
        db.add_all([
            Membership(workspace_id="ravens", user_id=owner, subject="owner", permissions=["forms.read", "forms.submissions.write"]),
            Membership(workspace_id="ravens", user_id=stranger, subject="stranger", permissions=["forms.read", "forms.submissions.write"]),
        ])
        db.commit()
    actor = {"id": owner}
    def override():
        with factory() as db:
            yield db
    app.dependency_overrides[get_db] = override
    app.dependency_overrides[current_user_id] = lambda: actor["id"]
    try:
        with TestClient(app) as client:
            yield client, actor, owner, stranger
    finally:
        app.dependency_overrides.clear()
        engine.dispose()

def test_create_list_update_and_owner_isolation(ctx):
    client, actor, owner, stranger = ctx
    created = client.post("/v1/workspaces/ravens/recruiting/contacts", json={"name":"Alex","notes":"Call later"})
    assert created.status_code == 201, created.text
    contact = created.json()
    assert contact["ownerId"] == owner
    assert client.get("/v1/workspaces/ravens/recruiting/contacts").json()["items"][0]["id"] == contact["id"]
    actor["id"] = stranger
    assert client.get("/v1/workspaces/ravens/recruiting/contacts").json()["items"] == []
    assert client.put(f"/v1/workspaces/ravens/recruiting/contacts/{contact['id']}", json={"name":"Hijack"}).status_code == 404
    actor["id"] = owner
    changed = client.put(f"/v1/workspaces/ravens/recruiting/contacts/{contact['id']}", json={"name":"Alex","status":"contacted"})
    assert changed.status_code == 200
    assert changed.json()["status"] == "contacted"

def test_reject_invalid_status_and_naive_followup(ctx):
    client, _, _, _ = ctx
    for body in ({"name":"Alex","status":"admin"}, {"name":"Alex","followUpAt":"2026-11-04T12:00:00"}):
        assert client.post("/v1/workspaces/ravens/recruiting/contacts", json=body).status_code == 422
