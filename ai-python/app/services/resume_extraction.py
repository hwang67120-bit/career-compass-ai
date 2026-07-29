"""이력서·포트폴리오 PDF 추출 결과를 개인정보 제거 후 LLM으로 구조화한다.

계약: contracts/document-extraction.md 7절 — 개인정보 제거 후에만 LLM을 호출하고,
Python이 반환하는 근거는 후보 값을 확인하는 최소 문장 범위로 제한한다.
"""

from app.guardrails.personal_information_sanitizer import sanitize_personal_information
from app.providers.ollama import OllamaProvider
from app.schemas.document import PageText
from app.schemas.profile_candidate import ProfileCandidatePayload


class EvidenceValidationError(RuntimeError):
    """LLM이 반환한 근거의 원문이 실제 페이지 내용과 일치하지 않는 경우다."""


def build_page_marked_text(pages: list[PageText]) -> str:
    """페이지별 원문을 개인정보 제거 후 '[페이지 N]' 표시로 합친다."""
    sections = [
        f"[페이지 {page.page_number}]\n{sanitize_personal_information(page.text)}"
        for page in pages
    ]
    return "\n\n".join(sections)


def _describe(item) -> str:
    """오류 메시지에 쓸, 항목을 식별할 수 있는 짧은 설명이다."""
    for attr in ("raw_name", "job_title", "project_name", "institution_name", "name"):
        value = getattr(item, attr, None)
        if value:
            return f"{type(item).__name__}({attr}={value!r})"
    return type(item).__name__


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
    """계약 5절의 근거 규칙을 검증한다.

    1. 모든 근거의 원문이 실제로 해당 페이지에 있는지(할루시네이션 차단).
    2. 근거 식별자가 중복되지 않는지.
    3. 모든 후보 항목이 존재하는 근거를 하나 이상 참조하는지.

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
        if not candidate_item.evidence_ids:
            raise EvidenceValidationError(
                f"{_describe(candidate_item)} 항목이 근거(evidenceIds)를 하나도 참조하지 않습니다."
            )
        for evidence_id in candidate_item.evidence_ids:
            if evidence_id not in seen_evidence_ids:
                raise EvidenceValidationError(
                    f"존재하지 않는 근거 '{evidence_id}'를 참조합니다."
                )


async def extract_resume_profile(
    pages: list[PageText], provider: OllamaProvider
) -> ProfileCandidatePayload:
    """개인정보를 제거한 원문을 provider로 구조화 추출하고 근거를 검증한다."""
    page_marked_text = build_page_marked_text(pages)
    candidate = await provider.extract_resume_profile(page_marked_text)
    validate_evidence(candidate, pages)
    return candidate
