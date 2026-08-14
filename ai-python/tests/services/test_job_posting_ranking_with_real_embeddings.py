import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings
from app.schemas.job_posting import JobPostingExtraction, JobPostingSkill
from app.services.job_posting_ranking import rank_job_postings

pytestmark = pytest.mark.real_gemini


@pytest.fixture
def provider() -> GeminiEmbeddingProvider:
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


def make_skill(raw_name: str) -> JobPostingSkill:
    return JobPostingSkill(raw_name=raw_name, evidence_ids=["e1"])


@pytest.mark.asyncio
async def test_rank_job_postings_orders_multiple_real_postings_by_relevance(
    provider: GeminiEmbeddingProvider,
) -> None:
    """"적합한 채용공고 재정렬"(로드맵 2번)을 실제 채용공고 여러 건으로 검증한다.

    새 재정렬 로직 없이 기존 rerank_candidates만으로 사용자 경험과 가장
    가까운 공고부터 순위가 매겨지고, 무관한 공고는 제외되는지 확인한다.
    """
    job_postings = {
        "java-spring-backend": JobPostingExtraction(
            job_title="백엔드 개발자",
            required_skills=[make_skill("Java"), make_skill("Spring Boot")],
            preferred_skills=[make_skill("PostgreSQL")],
        ),
        "python-fastapi-backend": JobPostingExtraction(
            job_title="백엔드 개발자",
            required_skills=[make_skill("Python"), make_skill("FastAPI")],
        ),
        "game-server-java": JobPostingExtraction(
            job_title="게임 서버 개발자",
            required_skills=[make_skill("Java"), make_skill("게임 서버 아키텍처")],
        ),
        "frontend-react": JobPostingExtraction(
            job_title="프론트엔드 개발자",
            required_skills=[make_skill("React"), make_skill("TypeScript")],
        ),
        "cafe-manager": JobPostingExtraction(
            job_title="카페 매장 관리자",
            required_skills=[make_skill("매장 운영"), make_skill("재고 관리")],
        ),
    }

    result = await rank_job_postings(
        provider,
        readme_texts={
            "README.md": (
                "이 프로젝트는 전자상거래 플랫폼의 주문·결제 백엔드다. "
                "Spring Boot와 PostgreSQL로 REST API를 구현했다."
            )
        },
        skill_names=["Java", "Spring Boot", "PostgreSQL"],
        job_postings=job_postings,
        minimum_similarity=0.65,
    )

    ranked_ids = [candidate.candidate_id for candidate in result.ranked]

    assert ranked_ids[0] == "java-spring-backend"
    assert "cafe-manager" not in ranked_ids
    assert "cafe-manager" in result.excluded_candidate_ids
