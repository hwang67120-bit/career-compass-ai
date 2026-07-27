"""환경변수 기반 서버 설정을 관리한다."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    app_name: str = "career-compass-ai-python"
    host: str = "0.0.0.0"
    port: int = 8000


@lru_cache
def get_settings() -> Settings:
    """설정 값을 한 번만 로드해 재사용한다."""
    return Settings()
