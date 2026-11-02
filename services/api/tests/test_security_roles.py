"""Authorization regression tests. Run: cd services/api && pytest -q"""
import os
from uuid import uuid4

os.environ.setdefault("FIELDHUB_OIDC_ISSUER", "http://localhost:8080/realms/fieldhub")
os.environ.setdefault("FIELDHUB_OIDC_AUDIENCE", "fieldhub-api")
os.environ.setdefault("FIELDHUB_OIDC_JWKS_URL", "http://keycloak:8080/realms/fieldhub/protocol/openid-connect/certs")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session
from sqlalchemy.pool import StaticPool

from app.database import Base, get_db
from app.main import app
from app.models import Workspace, User, Membership, WorkspaceRole, WorkspaceRoleAssignment, Form, Submission
from app.security import current_user_id, effective_permissions

ADMIN = str(uuid4())
SECOND = str(uuid4())
THIRD = str(uuid4())
OTHER = str(uuid4())
ROLE = str(uuid4())

@pytest.fixture
def env():
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False},
                           poolclass=StaticPool)
    @event.listens_for(engine, "connect")
    def fk(dbapi_conn, _):
        dbapi_conn.execute("PRAGMA foreign_keys=ON")
    Base.metadata.create_all(engine)
    with Session(engine, expire_on_commit=False) as db:
        db.add_all([Workspace(id="ravens", name="Ravens"), Workspace(id="other", name="Other")])
        for uid in [ADMIN, SECOND, THIRD, OTHER]:
            db.add(User(id=uid, display_name=uid))
        db.flush()
        db.add_all([
            Membership(workspace_id="ravens", user_id=ADMIN, subject="admin",
                       permissions=["workspace.manage", "forms.read"]),
            Membership(workspace_id="ravens", user_id=SECOND, subject="second",
                       permissions=["forms.read"]),
            Membership(workspace_id="ravens", user_id=THIRD, subject="third", permissions=[]),
            Membership(workspace_id="other", user_id=OTHER, subject="other", permissions=["workspace.manage"]),
        ])
        db.add(WorkspaceRole(id=ROLE, workspace_id="ravens", name="Trainer",
                             description="", permissions=["forms.read"]))
        db.commit()
    actor = {"id": ADMIN}
    def override_db():
        with Session(engine, expire_on_commit=False) as db:
            yield db
    app.dependency_overrides[get_db] = override_db
    app.dependency_overrides[current_user_id] = lambda: actor["id"]
    try:
        with TestClient(app) as client:
            yield client, actor, engine
    finally:
        app.dependency_overrides.clear()
        engine.dispose()

def test_role_grants_are_workspace_scoped(env):
    client, actor, engine = env
    response = client.put(f"/v1/workspaces/ravens/users/{THIRD}/roles", json={"roleIds": [ROLE]})
    assert response.status_code == 200, response.text
    with Session(engine) as db:
        assert "forms.read" in effective_permissions("ravens", THIRD, db)
        assert "forms.read" not in effective_permissions("other", THIRD, db)
    actor["id"] = THIRD
    assert client.get("/v1/workspaces/ravens/forms").status_code == 200
    assert client.get("/v1/workspaces/other/forms").status_code == 403

def test_cannot_delegate_unowned_permission(env):
    client, _, _ = env
    response = client.put(f"/v1/workspaces/ravens/users/{SECOND}",
                          json={"permissions": ["sync.write"]})
    assert response.status_code == 403
    response = client.post("/v1/workspaces/ravens/roles",
                           json={"name": "Super", "permissions": ["sync.write"]})
    assert response.status_code == 403

def test_last_admin_cannot_be_removed(env):
    client, actor, engine = env
    actor["id"] = ADMIN
    # Grant another administrator, then remove their admin right: permitted because ADMIN remains.
    response = client.put(f"/v1/workspaces/ravens/users/{SECOND}",
                          json={"permissions": ["workspace.manage", "forms.read"]})
    assert response.status_code == 200, response.text
    actor["id"] = SECOND
    response = client.put(f"/v1/workspaces/ravens/users/{ADMIN}",
                          json={"permissions": ["forms.read"]})
    assert response.status_code == 200, response.text
    # Now SECOND is the last administrator; ADMIN may not remove them.
    actor["id"] = ADMIN
    assert client.delete(f"/v1/workspaces/ravens/users/{SECOND}").status_code == 403
    # SECOND cannot remove itself either.
    actor["id"] = SECOND
    assert client.delete(f"/v1/workspaces/ravens/users/{SECOND}").status_code == 403

def test_self_assignment_denied(env):
    client, actor, _ = env
    assert client.put(f"/v1/workspaces/ravens/users/{ADMIN}/roles",
                      json={"roleIds": [ROLE]}).status_code == 403

def test_cross_workspace_role_denied(env):
    client, _, _ = env
    assert client.put(f"/v1/workspaces/other/users/{OTHER}/roles",
                      json={"roleIds": [ROLE]}).status_code == 403

def test_role_permissions_are_not_direct_grants(env):
    client, _, engine = env
    assert client.put(f"/v1/workspaces/ravens/users/{THIRD}/roles",
                      json={"roleIds": [ROLE]}).status_code == 200
    with Session(engine) as db:
        member = db.query(Membership).filter_by(workspace_id="ravens", user_id=THIRD).one()
        assert member.permissions == []
        assert effective_permissions("ravens", THIRD, db) == {"forms.read"}

def test_last_admin_role_cannot_be_stripped(env):
    client, actor, engine = env
    with Session(engine) as db:
        role = db.get(WorkspaceRole, ROLE)
        role.permissions = ["workspace.manage"]
        member = db.query(Membership).filter_by(workspace_id="ravens", user_id=ADMIN).one()
        member.permissions = ["forms.read"]
        db.add(WorkspaceRoleAssignment(workspace_id="ravens", role_id=ROLE, user_id=ADMIN))
        db.commit()
    response = client.put(f"/v1/workspaces/ravens/roles/{ROLE}",
                          json={"name": "Trainer", "permissions": []})
    assert response.status_code == 409, response.text
    with Session(engine) as db:
        assert "workspace.manage" in effective_permissions("ravens", ADMIN, db)

def test_workspace_rename_requires_management_permission(env):
    client, actor, engine = env
    actor["id"] = SECOND
    denied = client.patch("/v1/workspaces/ravens/admin", json={"name": "Unauthorized"})
    assert denied.status_code == 403
    actor["id"] = ADMIN
    allowed = client.patch("/v1/workspaces/ravens/admin", json={"name": "Ravens RFC"})
    assert allowed.status_code == 200, allowed.text
    assert allowed.json()["name"] == "Ravens RFC"
    actor["id"] = SECOND
    assert client.get("/v1/workspaces/ravens/admin").status_code == 403

def test_form_list_does_not_mix_workspaces(env):
    client, actor, engine = env
    with Session(engine) as db:
        db.add(Form(workspace_id="ravens", form_id="r1", version=1, title="R", definition={}, published=True))
        db.add(Form(workspace_id="other", form_id="o1", version=1, title="O", definition={}, published=True))
        db.commit()
    result = client.get("/v1/workspaces/ravens/forms")
    assert result.status_code == 200
    assert [item["id"] for item in result.json()["items"]] == ["r1"]
    assert client.get("/v1/workspaces/ravens/forms/o1").status_code == 404

def test_submission_list_is_workspace_scoped(env):
    client, actor, engine = env
    with Session(engine) as db:
        member = db.query(Membership).filter_by(workspace_id="ravens", user_id=ADMIN).one()
        member.permissions = ["workspace.manage", "forms.read", "forms.submissions.read"]
        db.add(Submission(id="r1", workspace_id="ravens", form_id="shared", form_version=1, values={"origin": "r"}, revision=1))
        db.add(Submission(id="o1", workspace_id="other", form_id="shared", form_version=1, values={"origin": "o"}, revision=1))
        db.commit()
    response = client.get("/v1/workspaces/ravens/forms/shared/submissions")
    assert response.status_code == 200
    assert [item["id"] for item in response.json()["items"]] == ["r1"]
    actor["id"] = SECOND
    assert client.get("/v1/workspaces/ravens/forms/shared/submissions").status_code == 403

def test_bootstrap_modules_respect_permissions(env):
    client, actor, engine = env
    response = client.get("/v1/bootstrap")
    assert response.status_code == 200, response.text
    workspace = response.json()["workspaces"][0]
    assert {m["id"] for m in workspace["modules"]} == {"forms", "administration"}
    assert {n["id"] for n in workspace["navigation"]} == {"forms", "members", "roles"}
    actor["id"] = SECOND
    response = client.get("/v1/bootstrap")
    assert response.status_code == 200, response.text
    workspace = response.json()["workspaces"][0]
    assert [m["id"] for m in workspace["modules"]] == ["forms"]
    assert [n["id"] for n in workspace["navigation"]] == ["forms"]

def test_module_allowlist_filters_navigation():
    from app.module_registry import visible_modules
    permissions = {"forms.read", "workspace.manage"}
    modules = visible_modules(permissions, {"forms"})
    assert [module["id"] for module in modules] == ["forms"]
    assert visible_modules(permissions, set()) == []
    assert {module["id"] for module in visible_modules(permissions)} == {"forms", "administration"}

def test_workspace_module_toggle_updates_bootstrap(env):
    client, actor, engine = env
    disabled = client.put("/v1/workspaces/ravens/modules/forms", json={"enabled": False})
    assert disabled.status_code == 200, disabled.text
    workspace = client.get("/v1/bootstrap").json()["workspaces"][0]
    assert [module["id"] for module in workspace["modules"]] == ["administration"]
    assert "forms" not in [item["id"] for item in workspace["navigation"]]
    enabled = client.put("/v1/workspaces/ravens/modules/forms", json={"enabled": True})
    assert enabled.status_code == 200, enabled.text
    workspace = client.get("/v1/bootstrap").json()["workspaces"][0]
    assert "forms" in [module["id"] for module in workspace["modules"]]

def test_module_administration_requires_workspace_manage(env):
    client, actor, engine = env
    actor["id"] = SECOND
    assert client.get("/v1/workspaces/ravens/modules").status_code == 403
    assert client.put("/v1/workspaces/ravens/modules/forms", json={"enabled": False}).status_code == 403
    actor["id"] = ADMIN
    assert client.put("/v1/workspaces/other/modules/forms", json={"enabled": False}).status_code == 403
    assert client.put("/v1/workspaces/ravens/modules/unknown", json={"enabled": False}).status_code == 404

def test_ui_schema_is_permission_scoped(env):
    client, actor, engine = env
    response = client.get("/v1/workspaces/ravens/ui")
    assert response.status_code == 200, response.text
    data = response.json()
    assert data["schemaVersion"] == 1
    assert {page["id"] for page in data["pages"]} == {"forms", "members", "roles"}
    actor["id"] = SECOND
    data = client.get("/v1/workspaces/ravens/ui").json()
    assert [page["id"] for page in data["pages"]] == ["forms"]
    assert [card["target"] for card in data["dashboard"]["cards"]] == ["forms"]
    actor["id"] = OTHER
    assert client.get("/v1/workspaces/ravens/ui").status_code == 403

def test_ui_schema_respects_module_toggle(env):
    client, actor, engine = env
    assert client.put("/v1/workspaces/ravens/modules/forms", json={"enabled": False}).status_code == 200
    data = client.get("/v1/workspaces/ravens/ui").json()
    assert "forms" not in [page["id"] for page in data["pages"]]
    assert "forms" not in [card["target"] for card in data["dashboard"]["cards"]]
    assert client.put("/v1/workspaces/ravens/modules/forms", json={"enabled": True}).status_code == 200
    data = client.get("/v1/workspaces/ravens/ui").json()
    assert "forms" in [page["id"] for page in data["pages"]]
