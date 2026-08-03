import pytest

from app.schemas.embedding import EmbeddingVector
from app.schemas.job_posting import JobPostingExtraction
from app.services.job_posting_ranking import rank_job_postings


class _FakeEmbeddingProvider:
    """네트워크 없이 오케스트레이션만 검증하기 위한 가짜 provider다.

    임베딩할 텍스트에 "backend"가 있으면 사용자 벡터와 같은 방향, 없으면
    직교하는 벡터를 반환해서 유사도 결과를 예측 가능하게 만든다.
    """

    provider_name = "fake"

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]:
        vectors = []
        for text in texts:
            values = [1.0, 0.0] if "backend" in text.lower() else [0.0, 1.0]
            vectors.append(
                EmbeddingVector(values=values, model="test-model", dimension=2, version="v1")
            )
        return vectors


@pytest.mark.asyncio
async def test_rank_job_postings_orders_by_similarity_and_applies_minimum() -> None:
    provider = _FakeEmbeddingProvider()

    result = await rank_job_postings(
        provider,
        readme_texts={"README.md": "backend service"},
        skill_names=["Java"],
        job_postings={
            "backend-posting": JobPostingExtraction(job_title="backend engineer"),
            "frontend-posting": JobPostingExtraction(job_title="frontend engineer"),
        },
        minimum_similarity=0.5,
    )

    ranked_ids = [candidate.candidate_id for candidate in result.ranked]
    assert ranked_ids == ["backend-posting"]
    assert result.excluded_candidate_ids == ["frontend-posting"]


@pytest.mark.asyncio
async def test_rank_job_postings_returns_all_when_minimum_similarity_is_low() -> None:
    provider = _FakeEmbeddingProvider()

    result = await rank_job_postings(
        provider,
        readme_texts={"README.md": "backend service"},
        skill_names=["Java"],
        job_postings={
            "backend-posting": JobPostingExtraction(job_title="backend engineer"),
            "frontend-posting": JobPostingExtraction(job_title="frontend engineer"),
        },
        minimum_similarity=-1.0,
    )

    assert len(result.ranked) == 2
    assert result.excluded_candidate_ids == []
