"""Authentication provider registry. Trusted installed modules only."""
from functools import lru_cache
from .config import settings

class ProviderRegistry:
    def __init__(self, providers, active):
        if active not in providers:
            raise ValueError("Unknown authentication provider")
        self.providers = providers
        self.active = active

    def validate(self, token):
        return self.providers[self.active].validate(token)

class OIDCProvider:
    def validate(self, token):
        from .security import validate_oidc_token
        return validate_oidc_token(token)

class KeycloakProvider(OIDCProvider):
    pass

@lru_cache(maxsize=1)
def get_registry():
    providers = {"oidc": OIDCProvider(), "keycloak": KeycloakProvider()}
    return ProviderRegistry(providers, settings.auth_provider)
