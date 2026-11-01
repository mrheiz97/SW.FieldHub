from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    database_url: str = "postgresql+psycopg://fieldhub:fieldhub@localhost:5432/fieldhub"
    oidc_issuer: str
    oidc_audience: str
    oidc_jwks_url: str
    model_config = SettingsConfigDict(env_prefix="FIELDHUB_", extra="ignore")

settings = Settings()
