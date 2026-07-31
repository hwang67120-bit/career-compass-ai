import pytest

from app.schemas.embedding import EmbeddingVector
from app.schemas.job_posting import JobPostingExtraction, JobPostingSkill
from app.services.job_posting_embedding import (
    JobPostingTextEmpty,
    build_job_posting_text,
    embed_job_posting,
)


class _FakeEmbeddingProvider:
    """네트워크 없이 오케스트레이션만 검증하기 위한 가짜 provider다."""

    def __init__(self) -> None:
        self.received_texts: list[str] | None = None

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]:
        self.received_texts = texts
        return [
            EmbeddingVector(values=[1.0, 0.0], model="test-model", dimension=2, version="v1")
            for _ in texts
        ]


def make_skill(raw_name: str) -> JobPostingSkill:
    return JobPostingSkill(raw_name=raw_name, evidence_ids=["e1"])


def test_build_job_posting_text_combines_title_and_skills() -> None:
    extraction = JobPostingExtraction(
        job_title="백엔드 개발자",
        required_skills=[make_skill("Java"), make_skill("Spring Boot")],
        preferred_skills=[make_skill("AWS")],
    )

    text = build_job_posting_text(extraction)

    assert "직무명: 백엔드 개발자" in text
    assert "필수 기술: Java, Spring Boot" in text
    assert "우대 기술: AWS" in text


def test_build_job_posting_text_works_with_only_required_skills() -> None:
    extraction = JobPostingExtraction(required_skills=[make_skill("Python")])

    text = build_job_posting_text(extraction)

    assert text == "필수 기술: Python"


def test_build_job_posting_text_raises_when_everything_empty() -> None:
    extraction = JobPostingExtraction()

    with pytest.raises(JobPostingTextEmpty):
        build_job_posting_text(extraction)


@pytest.mark.asyncio
async def test_embed_job_posting_passes_combined_text_to_provider() -> None:
    provider = _FakeEmbeddingProvider()
    extraction = JobPostingExtraction(job_title="백엔드 개발자")

    result = await embed_job_posting(provider, extraction)

    assert provider.received_texts == ["직무명: 백엔드 개발자"]
    assert result.model == "test-model"


@pytest.mark.asyncio
async def test_embed_job_posting_raises_before_calling_provider_when_empty() -> None:
    provider = _FakeEmbeddingProvider()

    with pytest.raises(JobPostingTextEmpty):
        await embed_job_posting(provider, JobPostingExtraction())

    assert provider.received_texts is None
