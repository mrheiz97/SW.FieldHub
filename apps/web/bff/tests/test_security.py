"""BFF security regression tests. Run: python -m unittest discover -s tests -v"""
import os
import sys
import unittest
import time
import asyncio
from unittest.mock import patch, AsyncMock
from types import SimpleNamespace
from pathlib import Path

os.environ.setdefault("BFF_OIDC_ISSUER","http://localhost:8080/realms/fieldhub")
os.environ.setdefault("BFF_OIDC_TOKEN_URL","http://keycloak:8080/realms/fieldhub/protocol/openid-connect/token")
os.environ.setdefault("BFF_REDIRECT_URI","http://127.0.0.1:8090/auth/callback")
os.environ.setdefault("BFF_SECURE_COOKIES","false")
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import main
from fastapi.testclient import TestClient

class SecurityTests(unittest.TestCase):
    def setUp(self):
        self.client=TestClient(main.app)

    def test_login_has_browser_bound_state_nonce_and_pkce(self):
        with patch.object(main.store,"setex",new_callable=AsyncMock) as save:
            response=self.client.get("/auth/login",follow_redirects=False)
        self.assertEqual(response.status_code,303)
        self.assertIn("fh_login_state=",response.headers["set-cookie"])
        self.assertIn("HttpOnly",response.headers["set-cookie"])
        self.assertIn("code_challenge_method=S256",response.headers["location"])
        self.assertIn("nonce=",response.headers["location"])
        self.assertEqual(save.await_count,1)

    def test_callback_rejects_unbound_state(self):
        response=self.client.get("/auth/callback?code=abc&state=xyz")
        self.assertEqual(response.status_code,400)

    def test_unauthenticated_api_rejected(self):
        response=self.client.get("/v1/bootstrap")
        self.assertEqual(response.status_code,401)

    def test_bad_nonce_rejected(self):
        with patch.object(main.jwks_client,"get_signing_key_from_jwt",return_value=SimpleNamespace(key="fake")):
            with patch.object(main.jwt,"decode",return_value={"nonce":"wrong"}):
                with self.assertRaises(ValueError):
                    main.verify_id_token("token","expected")

    def test_bad_jwt_signature_rejected(self):
        with patch.object(main.jwks_client,"get_signing_key_from_jwt",side_effect=main.jwt.PyJWTError("invalid")):
            with self.assertRaises(main.jwt.PyJWTError):
                main.verify_id_token("bad","nonce")

    def test_invalid_session_id_is_rejected_before_redis(self):
        with patch.object(main.store,"hgetall",new_callable=AsyncMock) as read:
            request=SimpleNamespace(cookies={main.cookie_name:"not valid/../id"})
            sid,data=asyncio.run(main.session(request))
        self.assertIsNone(data)
        self.assertIsNone(sid)
        read.assert_not_awaited()

    def test_idle_session_expires(self):
        now=int(time.time())
        data={"created":str(now-100),"last_seen":str(now-main.IDLE_SESSION_AGE-1),"csrf":"test"}
        with patch.object(main.store,"hgetall",new_callable=AsyncMock,return_value=data):
            with patch.object(main.store,"delete",new_callable=AsyncMock) as delete:
                sid,result=asyncio.run(main.session(SimpleNamespace(cookies={main.cookie_name:"valid_session"})))
        self.assertIsNone(result)
        self.assertEqual(sid,"valid_session")
        delete.assert_awaited_once()

    def test_active_session_updates_last_seen(self):
        now=int(time.time())
        data={"created":str(now-100),"last_seen":str(now-10),"csrf":"test"}
        with patch.object(main.store,"hgetall",new_callable=AsyncMock,return_value=data):
            with patch.object(main.store,"hset",new_callable=AsyncMock) as update:
                sid,result=asyncio.run(main.session(SimpleNamespace(cookies={main.cookie_name:"valid_session"})))
        self.assertEqual(result,data)
        update.assert_awaited_once()

    def test_origin_mismatch_rejected(self):
        from starlette.requests import Request
        req=Request({"type":"http","method":"POST","path":"/auth/logout","headers":[(b"host",b"127.0.0.1:8090"),(b"origin",b"https://attacker.example")],"scheme":"http","server":("127.0.0.1",8090)})
        with self.assertRaises(main.HTTPException) as exc:
            main.require_origin(req)
        self.assertEqual(exc.exception.status_code,403)

if __name__=="__main__":
    unittest.main()
