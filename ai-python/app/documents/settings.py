"""문서 추출 API의 환경변수 기반 설정을 관리한다."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class DocumentExtractionSettings(BaseSettings):
    """PDF 문서 추출 API에 필요한 설정이다."""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    document_extraction_max_pdf_size_bytes: int


@lru_cache
def get_document_extraction_settings() -> DocumentExtractionSettings:
    """설정 값을 한 번만 로드해 재사용한다."""
    return DocumentExtractionSettings()
