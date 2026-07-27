import httpx
import pytest
import pytest_asyncio

from app.providers.ollama import OllamaProvider
from app.providers.settings import OllamaSettings


@pytest.fixture
def settings() -> OllamaSettings:
    return OllamaSettings()


@pytest_asyncio.fixture
async def provider(settings: OllamaSettings):
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"),
        timeout=timeout,
    ) as client:
        yield OllamaProvider(client=client, model_name=settings.ollama_model)


@pytest.mark.asyncio
async def test_verify_model_passes_when_model_installed(provider: OllamaProvider) -> None:
    await provider.verify_model()


@pytest.mark.asyncio
async def test_extract_job_posting_returns_evidence_linked_result(
    provider: OllamaProvider,
) -> None:
    result = await provider.extract_job_posting(
        "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상, FastAPI 실무 경험."
    )

    assert result.job_title
    assert result.evidence
