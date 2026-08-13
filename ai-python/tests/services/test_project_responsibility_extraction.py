import json

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
            {"technologyTagId": "tag-react", "canonicalName": "React"},
        ],
        repositorySnapshot={
            "sourceUrl": "https://github.com/example/sample",
            "fetchedAt": "2026-08-12T08:00:00Z",
            "repositoryVersion": "abc123",
            "readmes": [
                {
                    "evidenceId": "readme-1",
                    "path": "README.md",
                    "text": "React와 TypeScript로 관리자 대시보드 UI를 구현했습니다.",
                }
            ],
            "files": [
                {
                    "evidenceId": "file-pkg",
                    "path": "package.json",
                    "fileType": "MANIFEST",
                    "relatedTechnologyTagIds": [],
                    "text": '{"dependencies": {"react": "^18.0.0", "express": "^4.0.0"}}',
                },
                {
                    "evidenceId": "file-src",
                    "path": "src/App.tsx",
                    "fileType": "SOURCE",
                    "relatedTechnologyTagIds": ["tag-react"],
                    "text": "export default function App() { return null; }",
                },
            ],
        },
    )


def test_grounding_score_overlap() -> None:
    assert grounding_score("React 대시보드 구현", "React 대시보드") == 1.0
    assert grounding_score("export default function App", "머신러닝 파이프라인 학습") < 0.3


@pytest.mark.asyncio
async def test_detected_technologies_raw_manifest_and_language() -> None:
    provider = FakeExtractionProvider([])
    data = await extract_project_evidence(_request(), provider)
    detected = {(d["detectedName"], d["source"]): d["evidenceIds"] for d in data["detectedTechnologies"]}

    # 매니페스트 raw 의존성 — Python 키워드 목록으로 거르지 않고 그대로
    assert detected[("react", "MANIFEST")] == ["file-pkg"]
    assert detected[("express", "MANIFEST")] == ["file-pkg"]
    # 확장자 언어
    assert detected[("TypeScript", "LANGUAGE")] == ["file-src"]
    # 표준 태그 id·findingStatus는 응답에 없다
    assert all("technologyTagId" not in d and "findingStatus" not in d for d in data["detectedTechnologies"])


def _manifest_request(files: list[dict]) -> ProjectResponsibilityRequest:
    return ProjectResponsibilityRequest(
        extractionTaskId="e1",
        projectSourceId="p1",
        selectedTechnologyTags=[{"technologyTagId": "tag-x", "canonicalName": "X"}],
        repositorySnapshot={
            "sourceUrl": "https://github.com/example/sample",
            "fetchedAt": "2026-08-12T08:00:00Z",
            "repositoryVersion": "abc123",
            "readmes": [],
            "files": files,
        },
    )


@pytest.mark.asyncio
async def test_detected_technologies_sorted_by_evidence_then_name_capped_30() -> None:
    # 계약(L69-70): 근거 개수 내림차순 → 정규화 이름 오름차순 → 상위 30개.
    # 두 매니페스트에 공통인 popular는 근거 2개라 이름과 무관하게 맨 앞이어야 한다.
    deps_a = {"popular": "1", **{f"a{index:02d}": "1" for index in range(20)}}
    deps_b = {"popular": "1", **{f"b{index:02d}": "1" for index in range(15)}}
    files = [
        {
            "evidenceId": "file-a",
            "path": "a/package.json",
            "fileType": "MANIFEST",
            "relatedTechnologyTagIds": [],
            "text": json.dumps({"dependencies": deps_a}),
        },
        {
            "evidenceId": "file-b",
            "path": "b/package.json",
            "fileType": "MANIFEST",
            "relatedTechnologyTagIds": [],
            "text": json.dumps({"dependencies": deps_b}),
        },
    ]
    provider = FakeExtractionProvider([])
    data = await extract_project_evidence(_manifest_request(files), provider)
    detected = data["detectedTechnologies"]

    # 감지 36개지만 상위 30개로 잘린다.
    assert len(detected) == 30
    # 근거 2개인 popular가 이름과 무관하게 맨 앞.
    assert detected[0]["detectedName"] == "popular"
    assert detected[0]["evidenceIds"] == ["file-a", "file-b"]
    # 나머지(근거 1개)는 정규화 이름 오름차순.
    rest = [item["detectedName"] for item in detected[1:]]
    assert rest == sorted(rest, key=str.lower)


@pytest.mark.asyncio
async def test_responsibility_valid_citation_no_tag_ids() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="관리자 대시보드 UI 구현", source_evidence_ids=["readme-1"])]
    )
    data = await extract_project_evidence(_request(), provider)
    resp = data["responsibilityEvidenceCandidates"]

    assert len(resp) == 1
    assert resp[0]["evidenceId"] == "project-responsibility-1"
    assert resp[0]["category"] == "PROJECT_RESPONSIBILITY"
    assert resp[0]["sourceEvidenceIds"] == ["readme-1"]
    assert resp[0]["confirmationStatus"] == "UNCONFIRMED"
    # 담당 업무 후보는 표준 태그 id를 반환하지 않는다
    assert "relatedTechnologyTagIds" not in resp[0]


@pytest.mark.asyncio
async def test_responsibility_invalid_citation_dropped() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="유령 근거 인용", source_evidence_ids=["ghost"])]
    )
    data = await extract_project_evidence(_request(), provider)
    assert data["responsibilityEvidenceCandidates"] == []


@pytest.mark.asyncio
async def test_responsibility_ungrounded_text_dropped() -> None:
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text="머신러닝 파이프라인 텐서플로 학습", source_evidence_ids=["readme-1"])]
    )
    data = await extract_project_evidence(_request(), provider)
    assert data["responsibilityEvidenceCandidates"] == []


@pytest.mark.asyncio
async def test_responsibility_oversized_text_dropped() -> None:
    # 근거·grounding은 통과하지만 500자를 넘는 후보는 잘라내지 않고 버린다(계약).
    long_text = "React 대시보드 구현 " * 40
    assert len(long_text) > 500
    request = ProjectResponsibilityRequest(
        extractionTaskId="e1",
        projectSourceId="p1",
        selectedTechnologyTags=[{"technologyTagId": "tag-react", "canonicalName": "React"}],
        repositorySnapshot={
            "sourceUrl": "https://github.com/example/sample",
            "fetchedAt": "2026-08-12T08:00:00Z",
            "repositoryVersion": "abc123",
            "readmes": [{"evidenceId": "readme-1", "path": "README.md", "text": long_text}],
            "files": [],
        },
    )
    provider = FakeExtractionProvider(
        [ProjectResponsibilityCandidate(text=long_text, source_evidence_ids=["readme-1"])]
    )
    data = await extract_project_evidence(request, provider)
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
