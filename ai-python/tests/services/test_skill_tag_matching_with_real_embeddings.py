import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings
from app.schemas.skill_tag_match import TagMatchRecommendation
from app.services.skill_tag_matching import match_skill_tag


@pytest.fixture
def provider() -> GeminiEmbeddingProvider:
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


@pytest.mark.asyncio
async def test_match_skill_tag_suggests_correction_for_transliteration(
    provider: GeminiEmbeddingProvider,
) -> None:
    """2026-07-31 확인: 실제 임베딩으로 스프링부트/Spring Boot 유사도 0.76 확인
    (app/services/skill_tag_matching.py의 임계값 산정 근거)."""
    result = await match_skill_tag(provider, "스프링부트", ["Spring Boot", "Python"])

    assert result.recommendation == TagMatchRecommendation.SUGGEST_CORRECTION
    assert result.best_match_tag == "Spring Boot"


@pytest.mark.asyncio
async def test_match_skill_tag_returns_no_match_for_unrelated_tag(
    provider: GeminiEmbeddingProvider,
) -> None:
    """2026-07-31 확인: 실제 임베딩으로 우쿨렐레/Spring Boot 유사도 0.56 확인."""
    result = await match_skill_tag(provider, "우쿨렐레", ["Spring Boot", "Python"])

    assert result.recommendation == TagMatchRecommendation.NO_MATCH
