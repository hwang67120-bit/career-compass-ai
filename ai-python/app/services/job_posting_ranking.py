"""사용자 경험과 여러 채용공고를 비교해 적합한 순서로 재정렬한다.

새 재정렬 로직은 없다 — 이미 검증된 `app/services/reranking.py`의
`rerank_candidates`를 그대로 쓴다. 여기서 하는 일은 사용자 경험 임베딩
(`user_profile_embedding.py`)과 채용공고 임베딩(`job_posting_embedding.py`)을
만들어 `rerank_candidates`에 넘기는 오케스트레이션뿐이다.
"""

from app.schemas.job_posting import JobPostingExtraction
from app.schemas.reranking import RerankResult
from app.services.job_posting_embedding import EmbeddingProvider, embed_job_posting
from app.services.reranking import rerank_candidates
from app.services.user_profile_embedding import embed_user_profile


async def rank_job_postings(
    provider: EmbeddingProvider,
    readme_texts: dict[str, str],
    skill_names: list[str],
    job_postings: dict[str, JobPostingExtraction],
    minimum_similarity: float,
) -> RerankResult:
    """사용자 경험 임베딩과 채용공고 임베딩들을 만들어 재정렬한다.

    입력:
        provider: 임베딩을 만들 provider. 사용자 경험과 모든 채용공고를
            같은 provider·모델로 임베딩해야 비교가 가능하다.
        readme_texts, skill_names: `embed_user_profile`과 동일.
        job_postings: 채용공고 식별자와 구조화 결과의 매핑.
        minimum_similarity: `rerank_candidates`와 동일 — 이 값보다 낮은
            공고는 순위에서 제외한다.

    반환:
        `rerank_candidates`와 동일한 결과.

    예외:
        UserProfileTextEmpty, JobPostingTextEmpty: 임베딩할 내용이 없는 경우.
        provider가 던지는 예외를 그대로 전달한다.
        EmbeddingMismatchError: `calculate_cosine_similarity`에서 발생.
    """
    user_vector = await embed_user_profile(provider, readme_texts, skill_names)

    job_posting_vectors = {
        job_posting_id: await embed_job_posting(provider, extraction)
        for job_posting_id, extraction in job_postings.items()
    }

    return rerank_candidates(
        query=user_vector,
        candidates=job_posting_vectors,
        minimum_similarity=minimum_similarity,
    )
