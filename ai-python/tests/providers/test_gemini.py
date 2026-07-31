import pytest
from google import genai

from app.providers.gemini import GeminiProvider
from app.providers.settings import GeminiSettings


@pytest.fixture
def settings() -> GeminiSettings:
    return GeminiSettings()


@pytest.fixture
def provider(settings: GeminiSettings) -> GeminiProvider:
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiProvider(client=client, model_name=settings.gemini_model)


def test_provider_name_is_gemini() -> None:
    """라우터가 modelProvider 응답 필드를 여기서 가져온다 — 리터럴로 박아두지 않는다."""
    assert GeminiProvider.provider_name == "gemini"


@pytest.mark.asyncio
async def test_extract_job_posting_returns_evidence_linked_result(
    provider: GeminiProvider,
) -> None:
    # 실제 이력서/공고가 아닌 직접 만든 가상 채용 공고만 사용한다 (Gemini 무료 등급 데이터 정책).
    result = await provider.extract_job_posting(
        "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상, FastAPI 실무 경험."
    )

    assert result.job_title
    assert result.evidence


@pytest.mark.asyncio
async def test_generate_job_search_keyword_suggestions_returns_list(
    provider: GeminiProvider,
) -> None:
    # 실제 사용자 값이 아닌 직접 만든 가상의 희망 직무·기술만 사용한다(Gemini 무료 등급 데이터 정책).
    result = await provider.generate_job_search_keyword_suggestions(
        "백엔드 개발자", ["Spring Boot", "Java"]
    )

    assert isinstance(result.keywords, list)
