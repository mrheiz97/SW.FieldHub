"""Workspace isolation regression tests for browser BFF and native Android API callers.

Run inside API container: python -m pytest tests/test_workspace_isolation.py -v
Uses an isolated in-memory SQLite database; no Keycloak or production data.
"""
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.models import (
    Form, Membership, User, Workspace, WorkspaceRole, WorkspaceRoleAssignment,
)
from app.security import current_user_id

RAVENS = "ravens"
STUMPF = "stumpfworks"
ADMIN = str(uuid4())
RAVENS_ONLY = str(uuid4())
STUMPF_ONLY = str(uuid4())
OUTSIDER = str(uuid4())
ROLE_ID = str(uuid4())
FORM_ID = "private-form"


@pytest.fixture
def environment():
    engine = create_engine(
        "sqlite+pysqlite:///:memory:",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    with factory() as db:
        db.add_all([
            Workspace(id=RAVENS, name="Ravens"),
            Workspace(id=STUMPF, name="stumpfWORKS"),
        ])
        for uid in (ADMIN, RAVENS_ONLY, STUMPF_ONLY, OUTSIDER):
            db.add(User(id=uid, display_name=uid))
        db.flush()
        db.add_all([
            Membership(workspace_id=RAVENS, subject="admin", user_id=ADMIN,
                       permissions=["workspace.manage", "forms.read"]),
            Membership(workspace_id=RAVENS, subject="ravens", user_id=RAVENS_ONLY,
                       permissions=["forms.read"]),
            Membership(workspace_id=STUMPF, subject="stumpf", user_id=STUMPF_ONLY,
                       permissions=["workspace.manage", "forms.read"]),
        ])
        db.add(WorkspaceRole(id=ROLE_ID, workspace_id=RAVENS, name="Ravens role",
                             description="", permissions=["forms.read"]))
        db.add(Form(workspace_id=RAVENS, form_id=FORM_ID, version=1,
                    title="Ravens private", definition={"type": "ravens"}, published=True))
        db.add(Form(workspace_id=STUMPF, form_id=FORM_ID, version=1,
                    title="SW private", definition={"type": "stumpf"}, published=True))
        db.commit()

    actor = {"id": RAVENS_ONLY}

    def override_db():
        with factory() as db:
            yield db

    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[current_user_id] = lambda: actor["id"]
    try:
        with TestClient(app) as client:
            yield client, actor, factory
    finally:
        app.dependency_overrides.clear()
        engine.dispose()


def test_bootstrap_only_returns_own_workspace(environment):
    client, actor, _ = environment
    for uid, expected in ((RAVENS_ONLY, RAVENS), (STUMPF_ONLY, STUMPF),
                          (OUTSIDER, None)):
        actor["id"] = uid
        response = client.get("/v1/bootstrap")
        assert response.status_code == 200
        ids = [w["id"] for w in response.json()["workspaces"]]
        assert ids == ([expected] if expected else [])


@pytest.mark.parametrize("path", [
    "/v1/workspaces/stumpfworks/ui",
    "/v1/workspaces/stumpfworks/forms",
    "/v1/workspaces/stumpfworks/forms/private-form",
    "/v1/workspaces/stumpfworks/forms/private-form/submissions",
    "/v1/workspaces/stumpfworks/users",
    "/v1/workspaces/stumpfworks/roles",
    "/v1/workspaces/stumpfworks/modules",
    "/v1/workspaces/stumpfworks/admin",
])
def test_cross_workspace_reads_denied(environment, path):
    client, actor, _ = environment
    actor["id"] = RAVENS_ONLY
    response = client.get(path)
    assert response.status_code in (403, 404), (path, response.text)


def test_cross_workspace_mutations_denied(environment):
    client, actor, _ = environment
    actor["id"] = ADMIN
    checks = [
        ("PUT", f"/v1/workspaces/{STUMPF}/users/{STUMPF_ONLY}",
         {"permissions": []}),
        ("PUT", f"/v1/workspaces/{STUMPF}/users/{STUMPF_ONLY}/roles",
         {"roleIds": []}),
        ("POST", f"/v1/workspaces/{STUMPF}/roles",
         {"name": "Injected", "permissions": []}),
        ("PATCH", f"/v1/workspaces/{STUMPF}/admin",
         {"name": "Compromised"}),
        ("PUT", f"/v1/workspaces/{STUMPF}/modules/forms",
         {"enabled": False}),
    ]
    for method, path, body in checks:
        response = client.request(method, path, json=body)
        assert response.status_code in (403, 404), (path, response.text)
    with environment[2]() as db:
        assert db.get(Workspace, STUMPF).name == "stumpfWORKS"


def test_foreign_role_cannot_be_assigned(environment):
    client, actor, factory = environment
    actor["id"] = STUMPF_ONLY
    response = client.put(
        f"/v1/workspaces/{STUMPF}/users/{STUMPF_ONLY}/roles",
        json={"roleIds": [ROLE_ID]},
    )
    # Self-edit is forbidden even before role validation.
    assert response.status_code == 403
    with factory() as db:
        assert db.query(WorkspaceRoleAssignment).count() == 0


def test_workspace_data_isolation_even_for_same_form_id(environment):
    client, actor, _ = environment
    actor["id"] = RAVENS_ONLY
    assert client.get(f"/v1/workspaces/{RAVENS}/forms/{FORM_ID}").json() == {"type": "ravens"}
    assert client.get(f"/v1/workspaces/{STUMPF}/forms/{FORM_ID}").status_code == 403
    actor["id"] = STUMPF_ONLY
    assert client.get(f"/v1/workspaces/{STUMPF}/forms/{FORM_ID}").json() == {"type": "stumpf"}
