"""저장소 스냅숏에서 감지 기술과 프로젝트 담당 업무 후보를 추출한다.

계약: contracts/project-responsibility-extraction.md. Java가 전달한 자료만
쓴다(GitHub 직접 조회 없음). 두 종류를 만든다:

- 감지 기술(`detectedTechnologies`): 결정론(LLM 없음). 매니페스트가 준 의존성
  식별자와 파일 확장자 언어를 원문 그대로 낸다. Python은 자체 키워드 목록으로
  거르거나 표준 이름으로 바꾸지 않고, 표준 태그 매핑은 하지 않는다 — 카탈로그를
  소유한 Java가 `technology-tag-resolution`으로 매핑·판정한다.
- 담당 업무 후보(`responsibilityEvidenceCandidates`): LLM. 근거 자료에서 담당
  업무를 뽑고 인용한 근거 id(`sourceEvidenceIds`)만 남긴다(표준 태그 ID 없음).

모든 후보는 `UNCONFIRMED`이며 사용자 확인 뒤 Java가 확정한다.
"""

from app.providers.ollama import OllamaProvider
from app.schemas.project_responsibility import ProjectResponsibilityRequest
from app.services.manifest_parsers import MANIFEST_DEPENDENCY_EXTRACTORS
from app.services.repository_evidence import _EXTENSION_LANGUAGES

# 인용 근거 대비 담당 업무 text의 최소 단어 겹침 — 근거 id는 맞는데 내용이 전혀 다른
# (지어낸) 후보를 거른다. 요약이라 정확 일치는 아니므로 낮은 바닥값만 둔다.
_GROUNDING_FLOOR = 0.3

# 계약(project-responsibility-extraction.md L69-70): 감지 기술은 근거 개수 내림차순,
# 정규화(소문자) detectedName 오름차순으로 정렬해 상위 30개만 반환한다. Java도 같은
# 상한(30)으로 응답을 검증한다.
_MAX_DETECTED_TECHNOLOGIES = 30

# 담당 업무 후보 text 상한(Unicode 코드 포인트). Java DB extracted_text·확인 요청
# confirmedText와 같은 500자다. 초과 후보는 잘라내지 않고 버린다.
_MAX_RESPONSIBILITY_TEXT_LENGTH = 500


def _tokens(text: str) -> list[str]:
    return "".join(c if c.isalnum() else " " for c in text.lower()).split()


def grounding_score(source_text: str, candidate_text: str) -> float:
    """담당 업무 text의 단어 중 몇 비율이 인용 근거에 실제로 있나(0.0~1.0)."""
    source_tokens = set(_tokens(source_text))
    candidate_tokens = [token for token in _tokens(candidate_text) if len(token) >= 2]
    if not candidate_tokens:
        return 0.0
    return sum(1 for token in candidate_tokens if token in source_tokens) / len(candidate_tokens)


def _detected_technologies(request: ProjectResponsibilityRequest) -> list[dict]:
    """스냅숏에서 감지한 기술을 원문 그대로 낸다(매핑·판정 없음).

    - MANIFEST: `fileType=MANIFEST` 파일 text를 형식별 파서로 파싱해 의존성
      식별자를 그대로 낸다(Python 키워드 목록으로 거르지 않음).
    - LANGUAGE: 파일 경로 확장자로 언어를 감지한다.
    같은 이름은 근거(evidenceIds)를 합친다. 계약대로 근거 개수 내림차순, 정규화
    detectedName 오름차순으로 정렬해 상위 30개만 남긴다.
    """
    snapshot = request.repository_snapshot

    manifest_ids: dict[str, list[str]] = {}
    for file in snapshot.files:
        if file.file_type != "MANIFEST":
            continue
        filename = file.path.rsplit("/", 1)[-1].lower()
        extractor = MANIFEST_DEPENDENCY_EXTRACTORS.get(filename)
        if extractor is None:
            continue
        for identifier in extractor(file.text):
            ids = manifest_ids.setdefault(identifier, [])
            if file.evidence_id not in ids:
                ids.append(file.evidence_id)

    language_ids: dict[str, list[str]] = {}
    for file in snapshot.files:
        for extension, language in _EXTENSION_LANGUAGES.items():
            if file.path.lower().endswith(extension):
                ids = language_ids.setdefault(language, [])
                if file.evidence_id not in ids:
                    ids.append(file.evidence_id)
                break

    detected = [
        {"detectedName": name, "source": "MANIFEST", "evidenceIds": ids}
        for name, ids in manifest_ids.items()
    ]
    detected += [
        {"detectedName": name, "source": "LANGUAGE", "evidenceIds": ids}
        for name, ids in language_ids.items()
    ]
    detected.sort(key=lambda item: (-len(item["evidenceIds"]), item["detectedName"].lower()))
    return detected[:_MAX_DETECTED_TECHNOLOGIES]


async def _responsibility_evidence(
    request: ProjectResponsibilityRequest, provider: OllamaProvider
) -> list[dict]:
    snapshot = request.repository_snapshot
    evidence_items = [(r.evidence_id, r.text) for r in snapshot.readmes]
    evidence_items += [(f.evidence_id, f.text) for f in snapshot.files]
    if not evidence_items:
        return []

    text_by_id = {evidence_id: text for evidence_id, text in evidence_items}

    extraction = await provider.extract_project_responsibilities(
        evidence_items, [tag.canonical_name for tag in request.selected_technology_tags]
    )

    results: list[dict] = []
    counter = 1
    for candidate in extraction.responsibilities:
        if len(candidate.text) > _MAX_RESPONSIBILITY_TEXT_LENGTH:
            continue  # 계약: 500자 초과 후보는 잘라내지 않고 버린다(Java DB 상한과 동일)
        cited = [eid for eid in candidate.source_evidence_ids if eid in text_by_id]
        if not cited:
            continue  # 근거 id가 없거나 입력에 없으면 버린다(지어내기 방지)
        cited_text = " ".join(text_by_id[eid] for eid in cited)
        if grounding_score(cited_text, candidate.text) < _GROUNDING_FLOOR:
            continue  # 근거 id는 맞는데 내용이 근거와 동떨어지면 버린다
        results.append(
            {
                "evidenceId": f"project-responsibility-{counter}",
                "category": "PROJECT_RESPONSIBILITY",
                "text": candidate.text,
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
    detected = _detected_technologies(request)
    responsibility_candidates = await _responsibility_evidence(request, provider)
    return {
        "extractionTaskId": request.extraction_task_id,
        "projectSourceId": request.project_source_id,
        "repositoryVersion": request.repository_snapshot.repository_version,
        "detectedTechnologies": detected,
        "responsibilityEvidenceCandidates": responsibility_candidates,
        "modelExecution": {
            "stage": "PROJECT_RESPONSIBILITY_EXTRACTION",
            "provider": "OLLAMA",
            "model": provider.model_name,
        },
    }
