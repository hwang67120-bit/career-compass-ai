import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings
from app.schemas.skill_tag_match import TagMatchRecommendation
from app.services.skill_tag_matching import match_skill_tag

pytestmark = pytest.mark.real_gemini


@pytest.fixture
def provider() -> GeminiEmbeddingProvider:
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


# 이 테스트 파일에서만 쓰는 예시 목록이다 — 실제 관리되는 고정 태그 목록이 아니다.
# 그 목록의 저장·관리는 Java 책임이고(docs/current-work.md 참고), Python은
# match_skill_tag 호출마다 태그 목록을 인자로 받을 뿐 직접 들고 있지 않는다.
_CANONICAL_TAGS = [
    "Spring Boot",
    "Kubernetes",
    "Python",
    "React",
    "Docker",
    "AWS",
    "MySQL",
    "FastAPI",
    "Django",
    "PostgreSQL",
    "Git",
    "Linux",
    "TypeScript",
    "Java",
    "GitHub",
]


@pytest.mark.asyncio
async def test_match_skill_tag_suggests_correction_for_transliteration(
    provider: GeminiEmbeddingProvider,
) -> None:
    """2026-07-31 확인: 실제 임베딩으로 스프링부트/Spring Boot가 1위(0.76),
    2위(Django 0.60)와 margin 0.156으로 뚜렷이 갈리는 것 확인
    (app/services/skill_tag_matching.py의 임계값·margin 산정 근거)."""
    canonical_vectors = await provider.embed(_CANONICAL_TAGS)

    result = await match_skill_tag(provider, "스프링부트", _CANONICAL_TAGS, canonical_vectors)

    assert result.recommendation == TagMatchRecommendation.SUGGEST_CORRECTION
    assert result.best_match_tag == "Spring Boot"


@pytest.mark.asyncio
async def test_match_skill_tag_returns_no_match_for_unrelated_tag(
    provider: GeminiEmbeddingProvider,
) -> None:
    """2026-07-31 확인: 실제 임베딩으로 우쿨렐레는 1위와도 유사도 0.59로 낮고
    1·2위 margin도 0.02로 작아 NO_MATCH가 된다."""
    canonical_vectors = await provider.embed(_CANONICAL_TAGS)

    result = await match_skill_tag(provider, "우쿨렐레", _CANONICAL_TAGS, canonical_vectors)

    assert result.recommendation == TagMatchRecommendation.NO_MATCH


@pytest.mark.asyncio
async def test_match_skill_tag_rejects_close_but_wrong_neighbors_via_margin(
    provider: GeminiEmbeddingProvider,
) -> None:
    """2026-07-31 확인: 대응하는 고정 태그가 없는 'Node.js'는 TypeScript(0.658)와
    Java(0.656)가 근소한 차이라 margin(0.002)이 임계값에 못 미쳐 NO_MATCH가
    된다 — 절대 유사도만으로는 놓쳤을 오탐을 margin이 걸러낸 사례."""
    canonical_vectors = await provider.embed(_CANONICAL_TAGS)

    result = await match_skill_tag(provider, "Node.js", _CANONICAL_TAGS, canonical_vectors)

    assert result.recommendation == TagMatchRecommendation.NO_MATCH
