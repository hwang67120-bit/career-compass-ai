import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings
from app.schemas.job_posting import JobPostingExtraction, JobPostingSkill
from app.services.job_posting_embedding import embed_job_posting
from app.services.similarity import calculate_cosine_similarity
from app.services.user_profile_embedding import embed_user_profile

pytestmark = pytest.mark.real_gemini


@pytest.fixture
def provider() -> GeminiEmbeddingProvider:
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


def make_skill(raw_name: str) -> JobPostingSkill:
    return JobPostingSkill(raw_name=raw_name, evidence_ids=["e1"])


@pytest.mark.asyncio
async def test_user_profile_embedding_ranks_matching_job_posting_higher(
    provider: GeminiEmbeddingProvider,
) -> None:
    """"기술·프로젝트 의미 유사도 계산"(로드맵 2번)을 실제 임베딩으로 처음 검증한다.

    같은 provider로 만든 사용자 경험 임베딩과 채용공고 임베딩이
    calculate_cosine_similarity로 실제 비교 가능한지, 그리고 관련 있는
    공고가 무관한 공고보다 더 높게 나오는지 확인한다.
    """
    user_vector = await embed_user_profile(
        provider,
        readme_texts={
            "README.md": (
                "이 프로젝트는 전자상거래 플랫폼의 주문·결제 백엔드다. "
                "Spring Boot와 PostgreSQL로 REST API를 구현했다."
            )
        },
        skill_names=["Java", "Spring Boot", "PostgreSQL"],
    )

    backend_posting = JobPostingExtraction(
        job_title="백엔드 개발자",
        required_skills=[make_skill("Java"), make_skill("Spring Boot")],
        preferred_skills=[make_skill("PostgreSQL")],
    )
    frontend_posting = JobPostingExtraction(
        job_title="프론트엔드 개발자",
        required_skills=[make_skill("React"), make_skill("TypeScript")],
    )

    backend_vector = await embed_job_posting(provider, backend_posting)
    frontend_vector = await embed_job_posting(provider, frontend_posting)

    backend_similarity = calculate_cosine_similarity(user_vector, backend_vector)
    frontend_similarity = calculate_cosine_similarity(user_vector, frontend_vector)

    assert backend_similarity > frontend_similarity
