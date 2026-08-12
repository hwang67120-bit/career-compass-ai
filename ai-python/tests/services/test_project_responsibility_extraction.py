import pytest

from app.schemas.project_responsibility import (
    ProjectResponsibilityCandidate,
    ProjectResponsibilityExtraction,
)
from app.services.project_responsibility_extraction import (
    extract_project_responsibilities,
    grounding_score,
)

_README = (
    "이 프로젝트는 Redis 캐시와 비동기 처리로 주문 API 응답 지연을 줄인 커머스 백엔드다. "
    "Spring Boot와 PostgreSQL을 사용한다."
)


class FakeExtractionProvider:
    def __init__(self, candidates: list[ProjectResponsibilityCandidate]) -> None:
        self._extraction = ProjectResponsibilityExtraction(responsibilities=candidates)
        self.calls: list[tuple[str, list[str]]] = []

    async def extract_project_responsibilities(self, readme_text, selected_tech):
        self.calls.append((readme_text, selected_tech))
        return self._extraction


def test_grounding_score_substring_and_none() -> None:
    # 원문에 그대로 있는 인용은 조사 차이와 무관하게 1.0
    assert grounding_score(_README, "Redis 캐시와 비동기 처리") == 1.0
    # 원문에 없는 기술은 낮게
    assert grounding_score(_README, "Kubernetes 오케스트레이션 구성") < 0.5


@pytest.mark.asyncio
async def test_grounded_quote_is_marked_grounded() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(responsibility="주문 API 성능 개선", evidence_quote="Redis 캐시와 비동기 처리로 주문 API 응답 지연을 줄인")]
    )
    results = await extract_project_responsibilities(_README, ["Spring Boot"], provider)

    assert len(results) == 1
    assert results[0]["status"] == "GROUNDED"
    assert results[0]["coverage"] >= 0.8
    assert results[0]["responsibility"] == "주문 API 성능 개선"


@pytest.mark.asyncio
async def test_fabricated_quote_is_needs_review() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(responsibility="Kafka 이벤트 스트리밍 구축", evidence_quote="Kafka로 대규모 이벤트 스트리밍 파이프라인을 구축")]
    )
    results = await extract_project_responsibilities(_README, ["Spring Boot"], provider)

    # 원문에 없는 내용(Kafka)을 덧붙이면 grounding이 낮아 NEEDS_REVIEW
    assert results[0]["status"] == "NEEDS_REVIEW"
    assert results[0]["coverage"] < 0.8


@pytest.mark.asyncio
async def test_empty_evidence_quote_is_dropped() -> None:
    provider = FakeExtractionProvider(
        [
            ProjectResponsibilityCandidate(responsibility="근거 없는 항목", evidence_quote="   "),
            ProjectResponsibilityCandidate(responsibility="주문 API 개선", evidence_quote="주문 API 응답 지연을 줄인"),
        ]
    )
    results = await extract_project_responsibilities(_README, [], provider)

    assert len(results) == 1  # 빈 근거 항목은 버려짐
    assert results[0]["responsibility"] == "주문 API 개선"


@pytest.mark.asyncio
async def test_empty_extraction_returns_empty() -> None:
    provider = FakeExtractionProvider([])
    results = await extract_project_responsibilities(_README, ["Spring Boot"], provider)
    assert results == []
