"""채용공고 구조화 결과를 임베딩한다.

`app/services/user_profile_embedding.py`가 만드는 사용자 경험 임베딩과
같은 공간에서 비교(`app/services/similarity.py`)할 수 있도록, 채용공고도
같은 방식(직무명+기술 목록을 텍스트로 합쳐서 임베딩)으로 만든다. 두
임베딩을 같은 provider(같은 모델·버전)로 만들어야 `calculate_cosine_similarity`가
비교를 허용한다.
"""

from typing import Protocol

from app.schemas.embedding import EmbeddingVector
from app.schemas.job_posting import JobPostingExtraction
from app.services.performance_tracking import measure_stage


class JobPostingTextEmpty(ValueError):
    """직무명도 기술도 없어서 임베딩할 내용이 없는 경우다."""


class EmbeddingProvider(Protocol):
    """`OllamaEmbeddingProvider`·`GeminiEmbeddingProvider`가 공통으로 구현하는 부분이다."""

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]: ...


def build_job_posting_text(extraction: JobPostingExtraction) -> str:
    """채용공고 구조화 결과를 하나의 임베딩 입력 텍스트로 합친다(순수 함수).

    근거(evidence)의 원문은 쓰지 않는다 — 직무명·기술명만으로 사용자 경험
    임베딩(직무명·기술 목록 위주)과 같은 수준의 텍스트를 만든다.

    입력:
        extraction: `contracts/job-posting-extraction.md` 응답의 `extraction` 필드.

    반환:
        직무명과 필수·우대 기술을 이어 붙인 텍스트.

    예외:
        JobPostingTextEmpty: 직무명과 기술 목록이 모두 비어 있는 경우.
    """
    sections: list[str] = []

    if extraction.job_title:
        sections.append(f"직무명: {extraction.job_title}")
    if extraction.required_skills:
        sections.append(
            "필수 기술: " + ", ".join(skill.raw_name for skill in extraction.required_skills)
        )
    if extraction.preferred_skills:
        sections.append(
            "우대 기술: " + ", ".join(skill.raw_name for skill in extraction.preferred_skills)
        )

    if not sections:
        raise JobPostingTextEmpty(
            "직무명과 기술 목록이 모두 비어 있어 임베딩할 내용이 없습니다."
        )

    return "\n".join(sections)


async def embed_job_posting(
    provider: EmbeddingProvider, extraction: JobPostingExtraction
) -> EmbeddingVector:
    """채용공고 구조화 결과를 임베딩한다.

    입력:
        provider: 임베딩을 만들 provider(`OllamaEmbeddingProvider` 등). 사용자
            경험 임베딩과 비교하려면 반드시 같은 provider·모델을 써야 한다.
        extraction: 채용공고 구조화 결과.

    반환:
        채용공고 임베딩 하나.

    예외:
        JobPostingTextEmpty: `build_job_posting_text`와 동일.
        provider가 던지는 예외(예: `EmbeddingUnavailableError`)를 그대로 전달한다.
    """
    text = build_job_posting_text(extraction)
    with measure_stage("embedding.embed_job_posting"):
        vectors = await provider.embed([text])
    return vectors[0]
