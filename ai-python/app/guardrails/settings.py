"""환경변수에서 내부 서비스 인증 설정을 읽는다."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class InternalAuthSettings(BaseSettings):
    """Java-Python 내부 호출 인증에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    internal_service_token: str


@lru_cache
def get_internal_auth_settings() -> InternalAuthSettings:
    """설정 값을 한 번만 로드해 재사용한다."""
    return InternalAuthSettings()
