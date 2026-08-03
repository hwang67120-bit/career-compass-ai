"""채용공고 원문을 LLM으로 구조화한다.

계약: contracts/job-posting-extraction.md (제안) — 개인정보 제거 단계가
없다(공개 회사 정보). 근거 검증·필터링은 이력서(`resume_extraction.py`)와
같은 원칙을 쓰되, 페이지 개념이 없어 pageNumber를 다루지 않는다.
"""

from app.providers.ollama import OllamaProvider
from app.schemas.job_posting import JobPostingExtraction, JobPostingSkill
from app.services.performance_tracking import measure_stage


class JobPostingEvidenceValidationError(RuntimeError):
    """LLM이 반환한 근거의 원문이 실제 채용공고 원문과 일치하지 않는 경우다."""


def _skill_items(payload: JobPostingExtraction) -> list[JobPostingSkill]:
    return [*payload.required_skills, *payload.preferred_skills]


def validate_evidence(payload: JobPostingExtraction, source_text: str) -> None:
    """할루시네이션·중복·유령 참조를 검증한다(문서 추출과 같은 원칙).

    "모든 후보 항목이 근거를 가져야 한다"는 계약 요건은 여기서 검증하지
    않는다 — `filter_unevidenced_candidates`가 근거 없는 항목을 응답에서
    제거해서 계약을 지킨다.
    """
    seen_evidence_ids: set[str] = set()
    for item in payload.evidence:
        if item.source_text not in source_text:
            raise JobPostingEvidenceValidationError(
                f"근거 '{item.evidence_id}'의 원문이 채용공고 원문에서 확인되지 않습니다."
            )
        if item.evidence_id in seen_evidence_ids:
            raise JobPostingEvidenceValidationError(f"근거 식별자 '{item.evidence_id}'가 중복됩니다.")
        seen_evidence_ids.add(item.evidence_id)

    all_referenced_ids = [
        evidence_id
        for item in _skill_items(payload)
        for evidence_id in item.evidence_ids
    ]
    all_referenced_ids.extend(payload.job_title_evidence_ids)
    for evidence_id in all_referenced_ids:
        if evidence_id not in seen_evidence_ids:
            raise JobPostingEvidenceValidationError(f"존재하지 않는 근거 '{evidence_id}'를 참조합니다.")


def filter_unevidenced_candidates(payload: JobPostingExtraction) -> JobPostingExtraction:
    """근거 없는 후보 항목을 제거하고, 아무도 참조하지 않는 근거도 함께 제거한다."""
    filtered_required = [s for s in payload.required_skills if s.evidence_ids]
    filtered_preferred = [s for s in payload.preferred_skills if s.evidence_ids]

    job_title = payload.job_title
    job_title_evidence_ids = payload.job_title_evidence_ids
    if not job_title_evidence_ids:
        job_title = None
        job_title_evidence_ids = []

    referenced_ids: set[str] = set(job_title_evidence_ids)
    for item in [*filtered_required, *filtered_preferred]:
        referenced_ids.update(item.evidence_ids)

    return payload.model_copy(
        update={
            "required_skills": filtered_required,
            "preferred_skills": filtered_preferred,
            "job_title": job_title,
            "job_title_evidence_ids": job_title_evidence_ids,
            "evidence": [e for e in payload.evidence if e.evidence_id in referenced_ids],
        }
    )


async def extract_job_posting_profile(
    source_text: str, provider: OllamaProvider
) -> JobPostingExtraction:
    """채용공고 원문을 provider로 구조화 추출하고 근거를 검증·정리한다."""
    with measure_stage(f"{provider.provider_name}.extract_job_posting"):
        candidate = await provider.extract_job_posting(source_text)
    validate_evidence(candidate, source_text)
    return filter_unevidenced_candidates(candidate)
