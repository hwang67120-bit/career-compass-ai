"""이력서·포트폴리오 PDF 추출 결과를 개인정보 제거 후 LLM으로 구조화한다.

계약: contracts/document-extraction.md 7절 — 개인정보 제거 후에만 LLM을 호출하고,
Python이 반환하는 근거는 후보 값을 확인하는 최소 문장 범위로 제한한다.
"""

from app.guardrails.personal_information_sanitizer import sanitize_personal_information
from app.providers.ollama import OllamaProvider
from app.schemas.document import PageText
from app.schemas.profile_candidate import CandidateProject, ProfileCandidatePayload
from app.services.performance_tracking import measure_stage


class EvidenceValidationError(RuntimeError):
    """LLM이 반환한 근거의 원문이 실제 페이지 내용과 일치하지 않는 경우다."""


def build_page_marked_text(pages: list[PageText]) -> str:
    """페이지별 원문을 개인정보 제거 후 '[페이지 N]' 표시로 합친다."""
    sections = [
        f"[페이지 {page.page_number}]\n{sanitize_personal_information(page.text)}"
        for page in pages
    ]
    return "\n\n".join(sections)


def _candidate_items(payload: ProfileCandidatePayload):
    """근거 참조 검증 대상이 되는 모든 후보 항목을 순회한다."""
    yield from payload.skills
    yield from payload.work_experiences
    yield from payload.projects
    for project in payload.projects:
        yield from project.technologies
    yield from payload.education
    yield from payload.certifications


def validate_evidence(payload: ProfileCandidatePayload, pages: list[PageText]) -> None:
    """계약 5절의 근거 규칙 중 할루시네이션·중복·유령 참조를 검증한다.

    1. 모든 근거의 원문이 실제로 해당 페이지에 있는지(할루시네이션 차단).
    2. 근거 식별자가 중복되지 않는지.
    3. 후보 항목이 참조하는 근거 ID가 실제로 존재하는지(유령 참조 차단).

    "모든 후보 항목이 근거를 하나 이상 가져야 한다"는 계약 요건은 여기서
    검증하지 않는다 — 대신 `filter_unevidenced_candidates`가 근거 없는
    항목을 응답에서 제거해서 계약을 지킨다(근거 없는 값을 사실처럼
    반환하는 대신, 조용히 빼는 방식).

    개인정보 제거 후 원문과 비교하므로, 제거된 개인정보는 애초에 근거로
    남을 수 없다.
    """
    sanitized_by_page = {
        page.page_number: sanitize_personal_information(page.text) for page in pages
    }

    seen_evidence_ids: set[str] = set()
    for item in payload.evidence:
        page_text = sanitized_by_page.get(item.page_number)
        if page_text is None or item.source_text not in page_text:
            raise EvidenceValidationError(
                f"근거 '{item.evidence_id}'의 원문이 페이지 {item.page_number}에서 확인되지 않습니다."
            )
        if item.evidence_id in seen_evidence_ids:
            raise EvidenceValidationError(f"근거 식별자 '{item.evidence_id}'가 중복됩니다.")
        seen_evidence_ids.add(item.evidence_id)

    for candidate_item in _candidate_items(payload):
        for evidence_id in candidate_item.evidence_ids:
            if evidence_id not in seen_evidence_ids:
                raise EvidenceValidationError(
                    f"존재하지 않는 근거 '{evidence_id}'를 참조합니다."
                )


def _filter_project(project: CandidateProject) -> CandidateProject:
    return project.model_copy(
        update={"technologies": [t for t in project.technologies if t.evidence_ids]}
    )


def filter_unevidenced_candidates(payload: ProfileCandidatePayload) -> ProfileCandidatePayload:
    """근거(evidenceIds)가 없는 후보 항목을 응답에서 제거하고, 그 결과 어떤
    후보 항목도 참조하지 않게 된 근거(evidence)도 함께 제거한다.

    계약 5절(최소 근거): 모든 후보 항목은 근거를 가져야 하고, Python이
    반환하는 근거는 후보 값을 확인하는 최소 범위로 제한한다. 후보 항목을
    제거하고 나서 아무도 참조하지 않는 근거를 응답에 그대로 남기면 이
    최소 범위 원칙을 어기고, sourceText에 남아있는 개인정보(예: 이름)가
    불필요하게 노출될 위험도 커진다.
    """
    filtered_skills = [s for s in payload.skills if s.evidence_ids]
    filtered_work_experiences = [w for w in payload.work_experiences if w.evidence_ids]
    filtered_projects = [_filter_project(p) for p in payload.projects if p.evidence_ids]
    filtered_education = [e for e in payload.education if e.evidence_ids]
    filtered_certifications = [c for c in payload.certifications if c.evidence_ids]

    referenced_ids: set[str] = set()
    for item in [
        *filtered_skills,
        *filtered_work_experiences,
        *filtered_education,
        *filtered_certifications,
    ]:
        referenced_ids.update(item.evidence_ids)
    for project in filtered_projects:
        referenced_ids.update(project.evidence_ids)
        for technology in project.technologies:
            referenced_ids.update(technology.evidence_ids)

    return payload.model_copy(
        update={
            "skills": filtered_skills,
            "work_experiences": filtered_work_experiences,
            "projects": filtered_projects,
            "education": filtered_education,
            "certifications": filtered_certifications,
            "evidence": [e for e in payload.evidence if e.evidence_id in referenced_ids],
        }
    )


async def extract_resume_profile(
    pages: list[PageText], provider: OllamaProvider
) -> ProfileCandidatePayload:
    """개인정보를 제거한 원문을 provider로 구조화 추출하고 근거를 검증·정리한다."""
    page_marked_text = build_page_marked_text(pages)
    with measure_stage(f"{provider.provider_name}.extract_resume_profile"):
        candidate = await provider.extract_resume_profile(page_marked_text)
    validate_evidence(candidate, pages)
    return filter_unevidenced_candidates(candidate)
