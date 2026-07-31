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


def test_provider_name_is_ollama() -> None:
    """라우터가 modelProvider 응답 필드를 여기서 가져온다 — 리터럴로 박아두지 않는다."""
    assert OllamaProvider.provider_name == "ollama"


@pytest.mark.asyncio
async def test_verify_model_passes_when_model_installed(provider: OllamaProvider) -> None:
    await provider.verify_model()


@pytest.mark.asyncio
async def test_extract_job_posting_returns_evidence_linked_result(
    provider: OllamaProvider,
) -> None:
    """확인 필요: qwen2.5로 실제 호출해보면 requiredSkills·evidence는 안정적으로
    채우지만, jobTitle은 채우지 않는 경우가 실제로 재현된다(모델이 근거가
    확실하지 않다고 판단하면 null로 남기는 지침을 따른 것으로 보임 —
    스키마·프롬프트가 아직 계약으로 확정되지 않아 더 튜닝하지 않았다)."""
    result = await provider.extract_job_posting(
        "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상, FastAPI 실무 경험."
    )

    assert result.evidence
    assert result.required_skills or result.preferred_skills


@pytest.mark.asyncio
async def test_generate_job_search_keyword_suggestions_returns_list(
    provider: OllamaProvider,
) -> None:
    result = await provider.generate_job_search_keyword_suggestions(
        "백엔드 개발자", ["Spring Boot", "Java"]
    )

    assert isinstance(result.keywords, list)
