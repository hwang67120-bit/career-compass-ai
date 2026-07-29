import pytest

from app.providers.ollama_client import get_ollama_provider
from app.providers.settings import OllamaSettings


@pytest.mark.asyncio
async def test_get_ollama_provider_uses_resume_model_setting() -> None:
    """documents/extract 라우터가 쓰는 provider는 OLLAMA_RESUME_MODEL 설정값을
    쓴다. 채용공고용 OLLAMA_MODEL과 같은 값이어도 무방하다 — 여기서
    확인하는 건 설정 경계(어떤 필드를 읽는지)이지, 두 값이 달라야 한다는
    정책이 아니다."""
    settings = OllamaSettings()

    async for provider in get_ollama_provider():
        assert provider.model_name == settings.ollama_resume_model
        break
