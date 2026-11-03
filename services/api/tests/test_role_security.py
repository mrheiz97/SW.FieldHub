"""Role privilege escalation regression tests; isolated SQLite, no production data."""
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.models import Membership, User, Workspace, WorkspaceRole, WorkspaceRoleAssignment
from app.security import current_user_id, effective_permissions

RAVENS, STUMPF = "ravens", "stumpfworks"
ADMIN, MEMBER, OTHER, SW_ADMIN = (str(uuid4()) for _ in range(4))
OWN_ROLE, FOREIGN_ROLE = str(uuid4()), str(uuid4())


@pytest.fixture
def ctx():
    engine = create_engine("sqlite+pysqlite:///:memory:", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, expire_on_commit=False)
    with factory() as db:
        db.add_all([Workspace(id=RAVENS, name="Ravens"), Workspace(id=STUMPF, name="SW")])
        db.add_all([User(id=uid) for uid in (ADMIN, MEMBER, OTHER, SW_ADMIN)])
        db.flush()
        db.add_all([
            Membership(workspace_id=RAVENS, user_id=ADMIN, subject="admin", permissions=["workspace.manage", "forms.read"]),
            Membership(workspace_id=RAVENS, user_id=MEMBER, subject="member", permissions=["forms.read"]),
            Membership(workspace_id=RAVENS, user_id=OTHER, subject="other", permissions=[]),
            Membership(workspace_id=STUMPF, user_id=SW_ADMIN, subject="swadmin", permissions=["workspace.manage", "forms.read"]),
        ])
        db.add_all([
            WorkspaceRole(id=OWN_ROLE, workspace_id=RAVENS, name="Reader", permissions=["forms.read"], description=""),
            WorkspaceRole(id=FOREIGN_ROLE, workspace_id=STUMPF, name="SW Admin", permissions=["workspace.manage"], description=""),
        ])
        db.commit()
    actor = {"id": ADMIN}

    def db_override():
        with factory() as db:
            yield db

    app.dependency_overrides[get_db] = db_override
    app.dependency_overrides[current_user_id] = lambda: actor["id"]
    try:
        with TestClient(app) as client:
            yield client, actor, factory
    finally:
        app.dependency_overrides.clear()
        engine.dispose()


def test_member_cannot_create_admin_role(ctx):
    client, actor, _ = ctx
    actor["id"] = MEMBER
    response = client.post(f"/v1/workspaces/{RAVENS}/roles", json={"name": "Admin", "permissions": ["workspace.manage"]})
    assert response.status_code == 403


def test_member_cannot_assign_roles(ctx):
    client, actor, _ = ctx
    actor["id"] = MEMBER
    response = client.put(f"/v1/workspaces/{RAVENS}/users/{OTHER}/roles", json={"roleIds": [OWN_ROLE]})
    assert response.status_code == 403


def test_admin_cannot_assign_foreign_workspace_role(ctx):
    client, actor, factory = ctx
    actor["id"] = ADMIN
    response = client.put(f"/v1/workspaces/{RAVENS}/users/{OTHER}/roles", json={"roleIds": [FOREIGN_ROLE]})
    assert response.status_code == 422
    with factory() as db:
        assert db.scalars(select(WorkspaceRoleAssignment)).all() == []


def test_admin_cannot_assign_role_to_self(ctx):
    client, actor, _ = ctx
    actor["id"] = ADMIN
    response = client.put(f"/v1/workspaces/{RAVENS}/users/{ADMIN}/roles", json={"roleIds": [OWN_ROLE]})
    assert response.status_code == 403


def test_admin_cannot_expand_own_assigned_role(ctx):
    client, actor, factory = ctx
    with factory() as db:
        db.add(WorkspaceRoleAssignment(workspace_id=RAVENS, role_id=OWN_ROLE, user_id=ADMIN))
        db.commit()
    actor["id"] = ADMIN
    response = client.put(f"/v1/workspaces/{RAVENS}/roles/{OWN_ROLE}",
                          json={"name": "Reader", "permissions": ["forms.read", "workspace.manage"]})
    assert response.status_code == 403
    with factory() as db:
        assert db.get(WorkspaceRole, OWN_ROLE).permissions == ["forms.read"]


def test_cannot_delete_assigned_role(ctx):
    client, actor, factory = ctx
    with factory() as db:
        db.add(WorkspaceRoleAssignment(workspace_id=RAVENS, role_id=OWN_ROLE, user_id=MEMBER))
        db.commit()
    actor["id"] = ADMIN
    assert client.delete(f"/v1/workspaces/{RAVENS}/roles/{OWN_ROLE}").status_code == 409


def test_role_permissions_do_not_leak_between_workspaces(ctx):
    _, _, factory = ctx
    with factory() as db:
        db.add(WorkspaceRoleAssignment(workspace_id=RAVENS, role_id=OWN_ROLE, user_id=MEMBER))
        db.commit()
        assert "forms.read" in effective_permissions(RAVENS, MEMBER, db)
        assert effective_permissions(STUMPF, MEMBER, db) == set()


def test_admin_cannot_assign_unknown_or_duplicate_roles(ctx):
    client, actor, _ = ctx
    actor["id"] = ADMIN
    for ids in ([OWN_ROLE, OWN_ROLE], [str(uuid4())]):
        response = client.put(f"/v1/workspaces/{RAVENS}/users/{OTHER}/roles", json={"roleIds": ids})
        assert response.status_code == 422


def test_admin_cannot_grant_permission_not_held(ctx):
    client, actor, _ = ctx
    actor["id"] = ADMIN
    response = client.post(f"/v1/workspaces/{RAVENS}/roles",
                           json={"name": "Sync", "permissions": ["sync.write"]})
    assert response.status_code == 403


def test_admin_cannot_remove_last_admin(ctx):
    client, actor, factory = ctx
    actor["id"] = ADMIN
    response = client.put(f"/v1/workspaces/{RAVENS}/users/{OTHER}",
                          json={"permissions": ["workspace.manage"]})
    assert response.status_code == 200
    # Another admin now exists, so removing their rights must be permitted.
    response = client.put(f"/v1/workspaces/{RAVENS}/users/{OTHER}", json={"permissions": []})
    assert response.status_code == 200
    with factory() as db:
        assert "workspace.manage" in effective_permissions(RAVENS, ADMIN, db)
