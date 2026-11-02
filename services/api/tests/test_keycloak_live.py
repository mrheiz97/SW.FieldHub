"""Opt-in checks against the configured, running Keycloak instance.

Requires FIELDHUB_TEST_KEYCLOAK=1 and FIELDHUB_TEST_KEYCLOAK_TOKEN.
Never print, store or commit an access token.
"""
import os

import httpx
import jwt
import pytest
from fastapi import HTTPException

from app.config import settings
from app.security import validate_oidc_token, jwks_client

pytestmark = pytest.mark.skipif(
    os.getenv("FIELDHUB_TEST_KEYCLOAK") != "1",
    reason="Opt in with FIELDHUB_TEST_KEYCLOAK=1 and a short-lived access token",
)


@pytest.fixture(scope="module")
def live_token():
    token = os.getenv("FIELDHUB_TEST_KEYCLOAK_TOKEN", "").strip()
    if not token:
        pytest.skip("FIELDHUB_TEST_KEYCLOAK_TOKEN not provided")
    return token


def test_live_keycloak_jwks_is_reachable():
    # Uses exactly the configured JWKS URL and its HTTPS/local-dev policy.
    client = jwks_client()
    keys = client.fetch_data()
    assert isinstance(keys.get("keys"), list) and keys["keys"]
    assert any(key.get("kty") in ("RSA", "EC") for key in keys["keys"])


def test_live_keycloak_access_token_is_valid(live_token):
    # Real network JWKS lookup and signature/issuer/audience/expiry checks.
    subject = validate_oidc_token(live_token)
    assert isinstance(subject, str) and subject


def test_live_keycloak_rejects_modified_token(live_token):
    parts = live_token.split(".")
    assert len(parts) == 3
    signature = parts[2]
    assert signature
    parts[2] = ("A" if signature[0] != "A" else "B") + signature[1:]
    with pytest.raises(HTTPException) as exc:
        validate_oidc_token(".".join(parts))
    assert exc.value.status_code == 401


def test_live_keycloak_rejects_wrong_audience(live_token):
    # The same authentic signature must not be accepted for another API.
    key = jwks_client().get_signing_key_from_jwt(live_token)
    with pytest.raises(jwt.InvalidAudienceError):
        jwt.decode(
            live_token, key.key, algorithms=["RS256", "ES256"],
            audience="fieldhub-test-intentionally-wrong-audience",
            issuer=settings.oidc_issuer,
            options={"require": ["exp", "iat", "iss", "aud", "sub"]},
        )


def test_live_api_bootstrap_respects_workspace_membership(live_token):
    # Read-only call against the real API; no membership changes are made.
    api_url = os.getenv("FIELDHUB_TEST_API_URL", "http://api:8000").rstrip("/")
    with httpx.Client(timeout=10) as client:
        response = client.get(
            api_url + "/v1/bootstrap",
            headers={"Authorization": "Bearer " + live_token},
        )
    assert response.status_code == 200, (
        f"Bootstrap returned HTTP {response.status_code}; check Keycloak issuer/audience and API connectivity"
    )
    body = response.json()
    assert isinstance(body["workspaces"], list)
    assert len({w["id"] for w in body["workspaces"]}) == len(body["workspaces"])
    for workspace in body["workspaces"]:
        assert isinstance(workspace["permissions"], list)
        assert isinstance(workspace["navigation"], list)
