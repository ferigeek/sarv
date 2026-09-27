from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    db_url: str
    analytics_user: str = "admin"
    analytics_password: str = "admin"
    analytics_secret_key: str = "dev-only-change-me"
    session_ttl_seconds: int = 12 * 3600

settings = Settings()