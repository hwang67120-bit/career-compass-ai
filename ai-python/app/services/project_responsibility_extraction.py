"""저장소 스냅숏에서 기술 근거와 프로젝트 담당 업무 후보를 추출한다.

계약: contracts/project-responsibility-extraction.md. Java가 전달한 자료만
쓴다(GitHub 직접 조회 없음). 두 종류를 만든다:

- 기술 근거(`technologyEvidenceCandidates`): 결정론(LLM 없음). 선택 기술이
  저장소 근거에 나타나는지 확인해 FOUND/NEEDS_REVIEW로 표시한다.
- 담당 업무 근거(`responsibilityEvidenceCandidates`): LLM. 근거 자료에서
  담당 업무를 뽑고 어떤 근거 id를 인용했는지 남긴다.

모든 후보는 `UNCONFIRMED`이며 사용자 확인 뒤 Java가 확정한다.
"""

from app.providers.ollama import OllamaProvider
from app.schemas.project_responsibility import ProjectResponsibilityRequest

# 인용 근거 대비 담당 업무 text의 최소 단어 겹침 — 근거 id는 맞는데 내용이 전혀 다른
# (지어낸) 후보를 거른다. 요약이라 정확 일치는 아니므로 낮은 바닥값만 둔다.
_GROUNDING_FLOOR = 0.3


def _tokens(text: str) -> list[str]:
    return "".join(c if c.isalnum() else " " for c in text.lower()).split()


def grounding_score(source_text: str, candidate_text: str) -> float:
    """담당 업무 text의 단어 중 몇 비율이 인용 근거에 실제로 있나(0.0~1.0)."""
    source_tokens = set(_tokens(source_text))
    candidate_tokens = [token for token in _tokens(candidate_text) if len(token) >= 2]
    if not candidate_tokens:
        return 0.0
    return sum(1 for token in candidate_tokens if token in source_tokens) / len(candidate_tokens)


def _technology_evidence(request: ProjectResponsibilityRequest) -> list[dict]:
    """선택 기술별 근거를 결정론으로 찾는다(A+B 혼합).

    A: 파일의 `relatedTechnologyTagIds`(Java가 미리 연결)를 신뢰한다.
    B: readme·파일 text에 `canonicalName`이 실제로 나타나면 근거로 보강한다.
    근거가 하나도 없으면 FOUND가 아니라 NEEDS_REVIEW다(오류·미보유가 아님).
    """
    snapshot = request.repository_snapshot
    results: list[dict] = []
    for tag in request.selected_technology_tags:
        evidence_ids: list[str] = []

        for file in snapshot.files:  # A
            if tag.technology_tag_id in file.related_technology_tag_ids:
                evidence_ids.append(file.evidence_id)

        name = tag.canonical_name.lower()  # B
        for readme in snapshot.readmes:
            if name in readme.text.lower() and readme.evidence_id not in evidence_ids:
                evidence_ids.append(readme.evidence_id)
        for file in snapshot.files:
            if name in file.text.lower() and file.evidence_id not in evidence_ids:
                evidence_ids.append(file.evidence_id)

        results.append(
            {
                "technologyTagId": tag.technology_tag_id,
                "canonicalName": tag.canonical_name,
                "findingStatus": "FOUND" if evidence_ids else "NEEDS_REVIEW",
                "evidenceIds": evidence_ids,
                "confirmationStatus": "UNCONFIRMED",
            }
        )
    return results


async def _responsibility_evidence(
    request: ProjectResponsibilityRequest, provider: OllamaProvider
) -> list[dict]:
    snapshot = request.repository_snapshot
    evidence_items = [(r.evidence_id, r.text) for r in snapshot.readmes]
    evidence_items += [(f.evidence_id, f.text) for f in snapshot.files]
    if not evidence_items:
        return []

    text_by_id = {evidence_id: text for evidence_id, text in evidence_items}
    tags_by_file = {f.evidence_id: f.related_technology_tag_ids for f in snapshot.files}

    extraction = await provider.extract_project_responsibilities(
        evidence_items, [tag.canonical_name for tag in request.selected_technology_tags]
    )

    results: list[dict] = []
    counter = 1
    for candidate in extraction.responsibilities:
        cited = [eid for eid in candidate.source_evidence_ids if eid in text_by_id]
        if not cited:
            continue  # 근거 id가 없거나 입력에 없으면 버린다(지어내기 방지)
        cited_text = " ".join(text_by_id[eid] for eid in cited)
        if grounding_score(cited_text, candidate.text) < _GROUNDING_FLOOR:
            continue  # 근거 id는 맞는데 내용이 근거와 동떨어지면 버린다
        related = sorted({tag for eid in cited for tag in tags_by_file.get(eid, [])})
        results.append(
            {
                "evidenceId": f"project-responsibility-{counter}",
                "category": "PROJECT_RESPONSIBILITY",
                "text": candidate.text,
                "relatedTechnologyTagIds": related,
                "sourceEvidenceIds": cited,
                "confirmationStatus": "UNCONFIRMED",
            }
        )
        counter += 1
    return results


async def extract_project_evidence(
    request: ProjectResponsibilityRequest, provider: OllamaProvider
) -> dict:
    """계약 성공 응답의 `data` 본문을 만든다.

    provider 예외(OllamaUnavailableError/OllamaResponseError)는 그대로 전달해
    라우터가 503/502로 변환한다.
    """
    technology_candidates = _technology_evidence(request)
    responsibility_candidates = await _responsibility_evidence(request, provider)
    return {
        "extractionTaskId": request.extraction_task_id,
        "projectSourceId": request.project_source_id,
        "repositoryVersion": request.repository_snapshot.repository_version,
        "technologyEvidenceCandidates": technology_candidates,
        "responsibilityEvidenceCandidates": responsibility_candidates,
        "modelExecution": {
            "stage": "PROJECT_RESPONSIBILITY_EXTRACTION",
            "provider": "OLLAMA",
            "model": provider.model_name,
        },
    }
