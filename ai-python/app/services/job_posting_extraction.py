"""채용공고 원문을 LLM으로 구조화한다.

계약: contracts/job-posting-extraction.md (제안) — 개인정보 제거 단계가
없다(공개 회사 정보). 근거 검증·필터링은 이력서(`resume_extraction.py`)와
같은 원칙을 쓰되, 페이지 개념이 없어 pageNumber를 다루지 않는다.

담당 업무(`responsibilities`)는 직무명·기술과 같은 호출로 묻지 않는다 —
하나로 합쳤을 때 qwen2.5의 evidence 배열 생성이 통째로 비어버리는 회귀가
실제로 재현됐다(2026-08-03). `extract_job_posting_profile`이 두 호출
(`OllamaProvider.extract_job_posting`, `extract_job_posting_responsibilities`)
을 독립적으로 실행·검증·재시도한 뒤 `_merge_core_and_responsibilities`로
합친다.
"""

from app.providers.ollama import OllamaProvider
from app.schemas.job_posting import (
    JobPostingCoreExtraction,
    JobPostingExtraction,
    JobPostingResponsibilityExtraction,
)
from app.services.performance_tracking import StageOperation, measure_stage

_EvidenceLinkedPayload = JobPostingCoreExtraction | JobPostingResponsibilityExtraction | JobPostingExtraction


class JobPostingEvidenceValidationError(RuntimeError):
    """LLM이 반환한 근거의 원문이 실제 채용공고 원문과 일치하지 않는 경우다."""


def validate_evidence(payload: _EvidenceLinkedPayload, source_text: str) -> None:
    """할루시네이션·중복·유령 참조를 검증한다(문서 추출과 같은 원칙).

    `JobPostingCoreExtraction`(담당 업무 없음)·`JobPostingResponsibilityExtraction`
    (담당 업무만)·병합된 `JobPostingExtraction`(전체) 세 타입에 모두 쓸 수
    있도록, 없는 필드는 `getattr` 기본값(빈 리스트)으로 건너뛴다.

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

    all_referenced_ids: list[str] = []
    for attr in ("responsibilities", "required_skills", "preferred_skills"):
        for item in getattr(payload, attr, []):
            all_referenced_ids.extend(item.evidence_ids)
    all_referenced_ids.extend(getattr(payload, "job_title_evidence_ids", []))

    for evidence_id in all_referenced_ids:
        if evidence_id not in seen_evidence_ids:
            raise JobPostingEvidenceValidationError(f"존재하지 않는 근거 '{evidence_id}'를 참조합니다.")


def filter_unevidenced_candidates(payload: JobPostingExtraction) -> JobPostingExtraction:
    """근거 없는 후보 항목을 제거하고, 아무도 참조하지 않는 근거도 함께 제거한다."""
    filtered_responsibilities = [r for r in payload.responsibilities if r.evidence_ids]
    filtered_required = [s for s in payload.required_skills if s.evidence_ids]
    filtered_preferred = [s for s in payload.preferred_skills if s.evidence_ids]

    job_title = payload.job_title
    job_title_evidence_ids = payload.job_title_evidence_ids
    if not job_title_evidence_ids:
        job_title = None
        job_title_evidence_ids = []

    referenced_ids: set[str] = set(job_title_evidence_ids)
    for item in [*filtered_responsibilities, *filtered_required, *filtered_preferred]:
        referenced_ids.update(item.evidence_ids)

    return payload.model_copy(
        update={
            "responsibilities": filtered_responsibilities,
            "required_skills": filtered_required,
            "preferred_skills": filtered_preferred,
            "job_title": job_title,
            "job_title_evidence_ids": job_title_evidence_ids,
            "evidence": [e for e in payload.evidence if e.evidence_id in referenced_ids],
        }
    )


def _merge_core_and_responsibilities(
    core: JobPostingCoreExtraction, responsibilities: JobPostingResponsibilityExtraction
) -> JobPostingExtraction:
    """두 독립 호출 결과를 하나의 `JobPostingExtraction`으로 합친다(순수 함수).

    두 호출은 서로 다른 LLM 요청이라 evidenceId가 우연히 겹칠 수 있다
    (둘 다 "e1"부터 시작하는 식). 담당 업무 쪽 evidenceId만 `r_` 접두사를
    붙여 재배정해서 병합 후 충돌을 막는다.
    """
    remapped_responsibilities = [
        item.model_copy(update={"evidence_ids": [f"r_{eid}" for eid in item.evidence_ids]})
        for item in responsibilities.responsibilities
    ]
    remapped_responsibility_evidence = [
        evidence_item.model_copy(update={"evidence_id": f"r_{evidence_item.evidence_id}"})
        for evidence_item in responsibilities.evidence
    ]

    return JobPostingExtraction(
        evidence=[*core.evidence, *remapped_responsibility_evidence],
        job_title=core.job_title,
        job_title_evidence_ids=core.job_title_evidence_ids,
        responsibilities=remapped_responsibilities,
        required_skills=core.required_skills,
        preferred_skills=core.preferred_skills,
    )


async def _extract_core_with_retry(
    source_text: str, provider: OllamaProvider
) -> JobPostingCoreExtraction:
    """직무명·기술을 추출·검증하고, 검증 실패 시 언로드 후 1회 재시도한다."""
    with measure_stage(provider.provider_name, StageOperation.EXTRACT_JOB_POSTING):
        candidate = await provider.extract_job_posting(source_text)
    try:
        validate_evidence(candidate, source_text)
    except JobPostingEvidenceValidationError:
        await provider.unload_model()
        with measure_stage(provider.provider_name, StageOperation.EXTRACT_JOB_POSTING):
            candidate = await provider.extract_job_posting(source_text)
        validate_evidence(candidate, source_text)
    return candidate


async def _extract_responsibilities_with_retry(
    source_text: str, provider: OllamaProvider
) -> JobPostingResponsibilityExtraction:
    """담당 업무를 추출·검증하고, 검증 실패 시 언로드 후 1회 재시도한다."""
    with measure_stage(provider.provider_name, StageOperation.EXTRACT_JOB_POSTING_RESPONSIBILITIES):
        candidate = await provider.extract_job_posting_responsibilities(source_text)
    try:
        validate_evidence(candidate, source_text)
    except JobPostingEvidenceValidationError:
        await provider.unload_model()
        with measure_stage(
            provider.provider_name, StageOperation.EXTRACT_JOB_POSTING_RESPONSIBILITIES
        ):
            candidate = await provider.extract_job_posting_responsibilities(source_text)
        validate_evidence(candidate, source_text)
    return candidate


async def extract_job_posting_profile(
    source_text: str,
    core_provider: OllamaProvider,
    responsibility_provider: OllamaProvider | None = None,
) -> JobPostingExtraction:
    """채용공고 원문을 provider로 구조화 추출하고 근거를 검증·정리한다.

    직무명·기술 추출과 담당 업무 추출을 독립된 두 호출로 나눠서 실행한다
    (이유는 위 모듈 docstring 참고). 두 호출은 서로 다른 모델을 쓸 수 있다 —
    2026-08-03 확인: qwen2.5는 담당 업무 추출에서 evidence를 계속 못 채웠고
    (4회+ 재현), exaone3.5는 같은 조건에서 6/6 성공했다. `responsibility_provider`를
    생략하면 `core_provider`를 그대로 쓴다(둘 다 같은 모델).

    각 호출은 근거 검증에 실패하면 모델을 한 번 언로드한 뒤 1회만 재시도한다
    — 같은 모델 로드 세션에서 다른 요청을 먼저 처리한 뒤에만 특정 입력의
    근거 검증이 실패하는 현상(세션 오염)을 실제로 확인했고, 언로드 직후 첫
    요청은 재현 시험에서 9/9 성공했다(2026-08-03, `docs/current-work.md`).
    재시도까지 실패하면 그대로 예외를 전달한다 — 세션과 무관한 진짜 모델
    결함은 언로드해도 재현되므로, 재시도가 그 결함을 감추지 않는다.
    """
    responsibility_provider = responsibility_provider or core_provider
    core = await _extract_core_with_retry(source_text, core_provider)
    responsibilities = await _extract_responsibilities_with_retry(
        source_text, responsibility_provider
    )
    merged = _merge_core_and_responsibilities(core, responsibilities)
    return filter_unevidenced_candidates(merged)
