"""채용공고 추출 API의 환경변수 기반 설정을 관리한다."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class JobPostingExtractionSettings(BaseSettings):
    """채용공고 텍스트 추출 API에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    job_posting_extraction_max_text_length: int = Field(gt=0)


@lru_cache
def get_job_posting_extraction_settings() -> JobPostingExtractionSettings:
    """설정 값을 한 번만 로드해 재사용한다."""
    return JobPostingExtractionSettings()
