import pytest

from app.schemas.project_responsibility import (
    ProjectResponsibilityCandidate,
    ProjectResponsibilityExtraction,
    ProjectResponsibilityRequest,
)
from app.services.project_responsibility_extraction import (
    extract_project_evidence,
    grounding_score,
)


class FakeExtractionProvider:
    def __init__(self, candidates: list[ProjectResponsibilityCandidate]) -> None:
        self._extraction = ProjectResponsibilityExtraction(responsibilities=candidates)
        self.model_name = "fake-extractor"
        self.calls: list[tuple] = []

    async def extract_project_responsibilities(self, evidence_items, selected_tech_names):
        self.calls.append((evidence_items, selected_tech_names))
        return self._extraction


def _request() -> ProjectResponsibilityRequest:
    return ProjectResponsibilityRequest(
        extractionTaskId="e1",
        projectSourceId="p1",
        selectedTechnologyTags=[
            {"technologyTagId": "tag-spring", "canonicalName": "Spring Boot"},
            {"technologyTagId": "tag-redis", "canonicalName": "Redis"},
            {"technologyTagId": "tag-kafka", "canonicalName": "Kafka"},
        ],
        repositorySnapshot={
            "sourceUrl": "https://github.com/example/sample",
            "fetchedAt": "2026-08-12T08:00:00Z",
            "repositoryVersion": "abc123",
            "readmes": [
                {
                    "evidenceId": "readme-1",
                    "path": "README.md",
                    "text": "Spring Boot로 주문 API를 구현했습니다. Redis 캐시를 적용했습니다.",
                }
            ],
            "files": [
                {
                    "evidenceId": "file-1",
                    "path": "src/OrderService.java",
                    "fileType": "SOURCE",
                    "relatedTechnologyTagIds": ["tag-spring"],
                    "text": "public Order createOrder() { ... }",
                }
            ],
        },
    )


def test_grounding_score_overlap() -> None:
    assert grounding_score("Spring Boot 주문 API 구현", "Spring Boot 주문") == 1.0
    assert grounding_score("public Order createOrder", "머신러닝 파이프라인 학습") < 0.3


@pytest.mark.asyncio
async def test_technology_evidence_found_and_needs_review() -> None:
    provider = FakeExtractionProvider([])  # 담당 업무는 이 테스트 대상 아님
    data = await extract_project_evidence(_request(), provider)
    tech = {t["technologyTagId"]: t for t in data["technologyEvidenceCandidates"]}

    # Spring Boot: 파일 relatedTag(A) + readme 텍스트(B) 둘 다 근거
    assert tech["tag-spring"]["findingStatus"] == "FOUND"
    assert set(tech["tag-spring"]["evidenceIds"]) == {"file-1", "readme-1"}
    # Redis: readme 텍스트에만 등장(B)
    assert tech["tag-redis"]["findingStatus"] == "FOUND"
    assert tech["tag-redis"]["evidenceIds"] == ["readme-1"]
    # Kafka: 어디에도 없음 → NEEDS_REVIEW
    assert tech["tag-kafka"]["findingStatus"] == "NEEDS_REVIEW"
    assert tech["tag-kafka"]["evidenceIds"] == []
    assert all(t["confirmationStatus"] == "UNCONFIRMED" for t in tech.values())


@pytest.mark.asyncio
async def test_responsibility_valid_citation_derives_related_tags() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="Spring Boot 기반 주문 API 구현", source_evidence_ids=["readme-1", "file-1"])]
    )
    data = await extract_project_evidence(_request(), provider)
    resp = data["responsibilityEvidenceCandidates"]

    assert len(resp) == 1
    assert resp[0]["evidenceId"] == "project-responsibility-1"
    assert resp[0]["category"] == "PROJECT_RESPONSIBILITY"
    assert resp[0]["sourceEvidenceIds"] == ["readme-1", "file-1"]
    # relatedTechnologyTagIds는 인용한 파일의 태그에서 유도(readme엔 태그 없음)
    assert resp[0]["relatedTechnologyTagIds"] == ["tag-spring"]
    assert resp[0]["confirmationStatus"] == "UNCONFIRMED"


@pytest.mark.asyncio
async def test_responsibility_invalid_citation_dropped() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="유령 근거 인용", source_evidence_ids=["ghost"])]
    )
    data = await extract_project_evidence(_request(), provider)
    assert data["responsibilityEvidenceCandidates"] == []


@pytest.mark.asyncio
async def test_responsibility_ungrounded_text_dropped() -> None:
    # 근거 id는 유효하지만 text가 근거와 완전히 동떨어짐 → 지어냄으로 보고 버림
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="머신러닝 파이프라인 텐서플로 학습 구축", source_evidence_ids=["file-1"])]
    )
    data = await extract_project_evidence(_request(), provider)
    assert data["responsibilityEvidenceCandidates"] == []


@pytest.mark.asyncio
async def test_model_execution_and_ids_echoed() -> None:
    provider = FakeExtractionProvider([])
    data = await extract_project_evidence(_request(), provider)
    assert data["extractionTaskId"] == "e1"
    assert data["projectSourceId"] == "p1"
    assert data["repositoryVersion"] == "abc123"
    assert data["modelExecution"] == {
        "stage": "PROJECT_RESPONSIBILITY_EXTRACTION",
        "provider": "OLLAMA",
        "model": "fake-extractor",
    }
