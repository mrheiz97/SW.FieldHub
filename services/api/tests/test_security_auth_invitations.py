"""Additional isolated authentication and invitation regression tests."""
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
import pytest
from fastapi import HTTPException
from sqlalchemy.orm import Session

from app import security
from app.models import WorkspaceInvitation, Membership
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

@pytest.fixture
def signed_oidc(monkeypatch):
    """Real RSA signatures and claim verification; JWKS lookup is stubbed."""
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    public = private.public_key()
    class SigningKey:
        key = public
    class JWKS:
        def get_signing_key_from_jwt(self, token):
            return SigningKey()
    monkeypatch.setattr(security, "jwks_client", lambda: JWKS())
    monkeypatch.setattr(security.settings, "oidc_issuer", "https://issuer.example")
    monkeypatch.setattr(security.settings, "oidc_audience", "fieldhub-api")
    def issue(**overrides):
        claims = {
            "sub": "test-user",
            "iss": "https://issuer.example",
            "aud": "fieldhub-api",
            "iat": datetime.now(timezone.utc),
            "exp": datetime.now(timezone.utc) + timedelta(minutes=5),
        }
        claims.update(overrides)
        return jwt.encode(claims, private, algorithm="RS256")
    return issue

def test_oidc_accepts_valid_rs256_signature(signed_oidc):
    assert security.validate_oidc_token(signed_oidc()) == "test-user"

def test_oidc_rejects_expired_rs256_signature(signed_oidc):
    token = signed_oidc(exp=datetime.now(timezone.utc) - timedelta(minutes=1))
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(token)
    assert error.value.status_code == 401

def test_oidc_rejects_wrong_issuer_rs256(signed_oidc):
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(signed_oidc(iss="https://attacker.example"))
    assert error.value.status_code == 401

def test_oidc_rejects_wrong_audience_rs256(signed_oidc):
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(signed_oidc(aud="different-api"))
    assert error.value.status_code == 401

def test_oidc_rejects_tampered_rs256_signature(signed_oidc):
    token = signed_oidc()
    header, payload, signature = token.split(".")
    changed = ("A" if signature[0] != "A" else "B")
    with pytest.raises(HTTPException) as error:
        security.validate_oidc_token(header + "." + payload + "." + changed + signature[1:])
    assert error.value.status_code == 401

def test_invitation_invalid_after_inviter_loses_admin_right(env):
    client, actor, engine = env
    created = client.post("/v1/workspaces/ravens/invitations",
                          json={"permissions": ["forms.read"], "expiresInHours": 1})
    assert created.status_code == 201, created.text
    with Session(engine) as db:
        member = db.query(Membership).filter_by(workspace_id="ravens", user_id=ADMIN).one()
        member.permissions = ["forms.read"]
        replacement = db.query(Membership).filter_by(workspace_id="ravens", user_id=SECOND).one()
        replacement.permissions = ["workspace.manage", "forms.read"]
        db.commit()
    actor["id"] = THIRD
    response = client.post("/v1/invitations/claim", json={"token": created.json()["token"]})
    assert response.status_code == 400, response.text
    with Session(engine) as db:
        assert db.query(Membership).filter_by(workspace_id="ravens", user_id=THIRD).one().permissions == []


def test_invitation_invalid_after_inviter_loses_delegated_permission(env):
    client, actor, engine = env
    created = client.post("/v1/workspaces/ravens/invitations",
                          json={"permissions": ["forms.read"], "expiresInHours": 1})
    assert created.status_code == 201, created.text
    with Session(engine) as db:
        member = db.query(Membership).filter_by(workspace_id="ravens", user_id=ADMIN).one()
        member.permissions = ["workspace.manage"]
        db.commit()
    actor["id"] = THIRD
    response = client.post("/v1/invitations/claim", json={"token": created.json()["token"]})
    assert response.status_code == 400, response.text
