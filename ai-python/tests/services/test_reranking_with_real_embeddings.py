import pytest
from google import genai

from app.providers.embedding import GeminiEmbeddingProvider
from app.providers.settings import GeminiSettings
from app.services.reranking import rerank_candidates


@pytest.fixture
def provider() -> GeminiEmbeddingProvider:
    settings = GeminiSettings()
    client = genai.Client(api_key=settings.gemini_api_key)
    return GeminiEmbeddingProvider(client=client, model_name=settings.gemini_embedding_model)


@pytest.mark.asyncio
async def test_rerank_candidates_ranks_matching_domain_highest(
    provider: GeminiEmbeddingProvider,
) -> None:
    # 실제 이력서·공고가 아닌 직접 만든 가상 예시만 사용한다.
    query_text = (
        "3년차 백엔드 개발자입니다. Java와 Spring Boot를 활용해 "
        "전자상거래 플랫폼의 주문 및 결제 시스템을 설계하고 구현했습니다."
    )
    candidate_texts = {
        "backend_posting": (
            "당사는 백엔드 개발자를 채용합니다. 필수 조건: Java 또는 Python 기반 "
            "서버 개발 경험 2년 이상, RDBMS 설계 경험, REST API 개발 경험."
        ),
        "frontend_posting": (
            "프론트엔드 개발자를 모집합니다. React 또는 Vue.js 실무 경험 2년 이상, "
            "TypeScript 사용 경험 필수."
        ),
        "unrelated_posting": (
            "카페 매장 관리자를 채용합니다. 매장 운영과 재고 관리, 고객 응대 경험자 우대."
        ),
    }

    vectors = await provider.embed([query_text, *candidate_texts.values()])
    query_vector = vectors[0]
    candidate_vectors = dict(zip(candidate_texts.keys(), vectors[1:]))

    result = rerank_candidates(query_vector, candidate_vectors, minimum_similarity=0.6)

    assert result.ranked[0].candidate_id == "backend_posting"
    assert "unrelated_posting" not in [c.candidate_id for c in result.ranked]
