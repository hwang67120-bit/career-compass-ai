import pytest

from app.providers.ollama_client import get_ollama_provider
from app.providers.settings import OllamaSettings


def test_ollama_model_and_resume_model_are_distinct_settings() -> None:
    """채용공고용 OLLAMA_MODEL과 이력서용 OLLAMA_RESUME_MODEL이 같은 값으로
    우연히 섞여 있지 않은지 확인한다(둘 다 설정돼 있고 서로 다름)."""
    settings = OllamaSettings()

    assert settings.ollama_model
    assert settings.ollama_resume_model
    assert settings.ollama_model != settings.ollama_resume_model


@pytest.mark.asyncio
async def test_get_ollama_provider_uses_resume_model_not_job_posting_model() -> None:
    """documents/extract 라우터가 쓰는 provider는 반드시 OLLAMA_RESUME_MODEL을
    쓴다 — 채용공고용 OLLAMA_MODEL이 섞여 들어가면 안 된다."""
    settings = OllamaSettings()

    async for provider in get_ollama_provider():
        assert provider.model_name == settings.ollama_resume_model
        assert provider.model_name != settings.ollama_model
        break
