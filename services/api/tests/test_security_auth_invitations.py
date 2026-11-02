"""Additional isolated authentication and invitation regression tests."""
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import jwt
import pytest
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app import security
from app.models import WorkspaceInvitation
from test_security_roles import env, ADMIN, SECOND, THIRD

def test_oidc_rejects_expired_token(monkeypatch):
    class Key:
        key = "unit-test-secret"
    class JWKS:
        def get_signing_key_from_jwt(self, token):
            return Key()
    monkeypatch.setattr(security, "jwks_client", lambda: JWKS())
    monkeypatch.setattr(security.settings, "oidc_issuer", "https://issuer.example")
    monkeypatch.setattr(security.settings, "oidc_audience", "fieldhub-api")
    claims = {"sub": "test", "iss": "https://issuer.example", "aud": "fieldhub-api",
              "iat": datetime.now(timezone.utc)-timedelta(hours=2),
              "exp": datetime.now(timezone.utc)-timedelta(hours=1)}
    token = jwt.encode(claims, "unit-test-secret", algorithm="HS256")
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(token)
    assert error.value.status_code == 401

def test_oidc_rejects_wrong_audience(monkeypatch):
    class Key:
        key = "unit-test-secret"
    class JWKS:
        def get_signing_key_from_jwt(self, token):
            return Key()
    monkeypatch.setattr(security, "jwks_client", lambda: JWKS())
    monkeypatch.setattr(security.settings, "oidc_issuer", "https://issuer.example")
    monkeypatch.setattr(security.settings, "oidc_audience", "fieldhub-api")
    claims = {"sub": "test", "iss": "https://issuer.example", "aud": "wrong-client",
              "iat": datetime.now(timezone.utc), "exp": datetime.now(timezone.utc)+timedelta(hours=1)}
    token = jwt.encode(claims, "unit-test-secret", algorithm="HS256")
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(token)
    assert error.value.status_code == 401

def test_invite_cannot_delegate_unowned_grant(env):
    client, _, _ = env
    response = client.post("/v1/workspaces/ravens/invitations",
                           json={"permissions": ["sync.write"], "expiresInHours": 1})
    assert response.status_code == 403

def test_invite_never_returns_secret_in_listing(env):
    client, _, engine = env
    response = client.post("/v1/workspaces/ravens/invitations",
                           json={"permissions": ["forms.read"], "expiresInHours": 1})
    assert response.status_code == 201, response.text
    token = response.json()["token"]
    with Session(engine) as db:
        stored = db.query(WorkspaceInvitation).one()
        assert stored.token_hash != token
        assert len(stored.token_hash) == 64
    listed = client.get("/v1/workspaces/ravens/invitations")
    assert listed.status_code == 200
    assert token not in listed.text

def test_revoked_invitation_cannot_be_claimed(env):
    client, actor, _ = env
    created = client.post("/v1/workspaces/ravens/invitations",
                          json={"permissions": ["forms.read"], "expiresInHours": 1})
    assert created.status_code == 201, created.text
    invitation = created.json()
    revoked = client.delete(f"/v1/workspaces/ravens/invitations/{invitation['id']}")
    assert revoked.status_code == 204, revoked.text
    actor["id"] = THIRD
    claim = client.post("/v1/invitations/claim", json={"token": invitation["token"]})
    assert claim.status_code == 400
