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

2026-08-04 추가 — Gemini 폴백: Ollama(로컬 서버)가 재시도까지 실패하면
Gemini로 폴백한다. 오늘 채용공고 비교 평가에서 Ollama 통과율이 이전보다
크게 떨어지는 현상이 실제로 있었는데, 같은 fixture를 Gemini로 교차
검증하면 매번 깨끗하게 통과했다 — Ollama 로컬 서버 쪽 문제로 보인다.

2026-08-04 PR #45 리뷰 반영 — 아래 세 가지를 고쳤다:

1. Gemini는 외부 서비스다. "채용공고는 공개 정보라 Gemini 무료 등급 데이터
   정책 확인이 필요 없다"는 판단은 근거 없는 자체 추론이었다 — 지금은 이
   판단을 쓰지 않는다. 실제 데이터 전송 범위(무엇을 어디까지 보내도 되는지)는
   아직 계약으로 확정되지 않았다(확인 필요). 대신 최소한의 방어선으로,
   Gemini에 보내기 직전 이메일·전화번호를 제거한다
   (`app/guardrails/contact_info_redaction.py`) — Ollama는 로컬 실행이라
   외부 전송이 아니므로 이 처리를 거치지 않는다.
2. Gemini 설정(`GEMINI_API_KEY` 등)이 없어도 Ollama만으로 이 API가 정상
   동작해야 한다. `fallback_provider_factory`는 실제 Ollama 실패가
   확인된 뒤에만 호출되는 지연 생성 콜백이다 — 매 요청마다 Gemini 클라이언트를
   미리 만들지 않는다(`app/providers/gemini_client.py`).
3. 직무명·기술(core)과 담당 업무 중 어느 쪽이 어느 provider·모델에서
   나왔는지를 숨기지 않는다. 응답은 단일 `modelProvider`/`modelName` 대신
   `JobPostingExtractionResult.model_executions`(단계별 provider·모델 목록)를
   반환한다 — core/responsibility가 서로 다른 Ollama 모델을 쓰는 기존
   구성에서도 이미 숨겨져 있던 정보다.
"""

import logging
from dataclasses import dataclass
from enum import Enum

from app.guardrails.contact_info_redaction import redact_contact_info
from app.providers.gemini import GeminiResponseError, GeminiUnavailableError
from app.providers.ollama import OllamaProvider, OllamaResponseError, OllamaUnavailableError
from app.schemas.job_posting import (
    JobPostingCoreExtraction,
    JobPostingExtraction,
    JobPostingResponsibilityExtraction,
)
from app.services.performance_tracking import StageOperation, measure_stage

_logger = logging.getLogger("app.job_posting_extraction")

_EvidenceLinkedPayload = JobPostingCoreExtraction | JobPostingResponsibilityExtraction | JobPostingExtraction


class JobPostingEvidenceValidationError(RuntimeError):
    """LLM이 반환한 근거의 원문이 실제 채용공고 원문과 일치하지 않는 경우다."""


_OllamaCoreFailure = (JobPostingEvidenceValidationError, OllamaResponseError, OllamaUnavailableError)
_GeminiFailure = (JobPostingEvidenceValidationError, GeminiResponseError, GeminiUnavailableError)


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
        _logger.info(
            "[재시도] 직무명·기술 추출 근거 검증 실패 (provider=%s) → 모델 언로드 후 1회 재시도",
            provider.provider_name,
        )
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
        _logger.info(
            "[재시도] 담당 업무 추출 근거 검증 실패 (provider=%s) → 모델 언로드 후 1회 재시도",
            provider.provider_name,
        )
        await provider.unload_model()
        with measure_stage(
            provider.provider_name, StageOperation.EXTRACT_JOB_POSTING_RESPONSIBILITIES
        ):
            candidate = await provider.extract_job_posting_responsibilities(source_text)
        validate_evidence(candidate, source_text)
    return candidate


class ModelExecutionStage(str, Enum):
    """`modelExecutions[].stage` 계약값이다. 자유 문자열을 받지 않는다."""

    CORE_EXTRACTION = "CORE_EXTRACTION"
    RESPONSIBILITY_EXTRACTION = "RESPONSIBILITY_EXTRACTION"


@dataclass
class ModelExecution:
    """`modelExecutions` 배열의 한 항목 — 어느 단계를 어느 provider·모델이 처리했는지."""

    stage: ModelExecutionStage
    provider: str
    model: str


@dataclass
class JobPostingExtractionResult:
    """추출 결과와 단계별(core/responsibility) 실행 provider·모델 이력이다.

    core와 담당 업무는 서로 다른 provider·모델을 쓸 수 있다(기본 설정도
    Ollama 안에서 core=qwen2.5, responsibility=exaone3.5로 이미 다르다).
    Gemini로 폴백한 단계가 있으면 그 단계만 provider가 "gemini"로 바뀐다 —
    혼합 실행을 단일 modelProvider/modelName 필드로 뭉개 감추지 않는다.
    """

    extraction: JobPostingExtraction
    model_executions: list[ModelExecution]


def _core_from_gemini(gemini_result: JobPostingExtraction) -> JobPostingCoreExtraction:
    return JobPostingCoreExtraction(
        evidence=gemini_result.evidence,
        job_title=gemini_result.job_title,
        job_title_evidence_ids=gemini_result.job_title_evidence_ids,
        required_skills=gemini_result.required_skills,
        preferred_skills=gemini_result.preferred_skills,
    )


def _responsibilities_from_gemini(
    gemini_result: JobPostingExtraction,
) -> JobPostingResponsibilityExtraction:
    return JobPostingResponsibilityExtraction(
        evidence=gemini_result.evidence,
        responsibilities=gemini_result.responsibilities,
    )


async def extract_job_posting_profile(
    source_text: str,
    core_provider: OllamaProvider,
    responsibility_provider: OllamaProvider | None = None,
    fallback_provider_factory=None,
) -> JobPostingExtractionResult:
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

    두 호출이 재시도까지 실패하면 `fallback_provider_factory`(Gemini)로
    폴백한다(2026-08-04, 위 모듈 docstring 참고) — 실패한 쪽만 채운다.
    둘 다 실패했으면 Gemini를 한 번만 호출해서 두 쪽 다 채운다(요청 수를
    아끼기 위해). `fallback_provider_factory`가 없거나(Gemini 설정 자체가
    없는 경우 포함) Gemini도 실패하면 Ollama 쪽 원래 예외를 그대로 전달한다
    — 실패를 감추지 않는다.

    입력:
        fallback_provider_factory: 인자 없이 호출하면 `GeminiProvider`를
            내주는 비동기 컨텍스트 매니저를 반환하는 콜백(예:
            `functools.partial(build_gemini_job_posting_fallback_provider, settings)`).
            Ollama 쪽이 실제로 실패했을 때만 호출된다 — Gemini 설정이 없어도
            이 함수가 정상 동작해야 하므로, 미리 provider 인스턴스를 만들어
            넘기지 않고 지연 생성 콜백만 받는다.
    """
    responsibility_provider = responsibility_provider or core_provider

    core: JobPostingCoreExtraction | None = None
    responsibilities: JobPostingResponsibilityExtraction | None = None
    core_error: Exception | None = None
    responsibilities_error: Exception | None = None

    try:
        core = await _extract_core_with_retry(source_text, core_provider)
    except _OllamaCoreFailure as error:
        core_error = error

    try:
        responsibilities = await _extract_responsibilities_with_retry(
            source_text, responsibility_provider
        )
    except _OllamaCoreFailure as error:
        responsibilities_error = error

    core_execution_provider = core_provider.provider_name
    core_execution_model = core_provider.model_name
    responsibility_execution_provider = responsibility_provider.provider_name
    responsibility_execution_model = responsibility_provider.model_name

    if core_error is not None or responsibilities_error is not None:
        if fallback_provider_factory is None:
            raise core_error or responsibilities_error

        cleaned_source_text = redact_contact_info(source_text)
        try:
            async with fallback_provider_factory() as fallback_provider:
                with measure_stage(fallback_provider.provider_name, StageOperation.EXTRACT_JOB_POSTING):
                    gemini_result = await fallback_provider.extract_job_posting(cleaned_source_text)
                validate_evidence(gemini_result, cleaned_source_text)
                fallback_provider_name = fallback_provider.provider_name
                fallback_model_name = fallback_provider.model_name
        except _GeminiFailure as gemini_error:
            raise (core_error or responsibilities_error) from gemini_error

        if core_error is not None:
            core = _core_from_gemini(gemini_result)
            core_execution_provider = fallback_provider_name
            core_execution_model = fallback_model_name
        if responsibilities_error is not None:
            responsibilities = _responsibilities_from_gemini(gemini_result)
            responsibility_execution_provider = fallback_provider_name
            responsibility_execution_model = fallback_model_name

    merged = _merge_core_and_responsibilities(core, responsibilities)
    extraction = filter_unevidenced_candidates(merged)
    _logger.info(
        "[채용공고 추출 완료] 직무명 추출됨=%s, 필수기술 %d개, 우대기술 %d개, "
        "담당업무 %d개, 근거 %d개 "
        "| 직무명·기술: %s/%s%s "
        "| 담당업무: %s/%s%s",
        extraction.job_title is not None,
        len(extraction.required_skills), len(extraction.preferred_skills),
        len(extraction.responsibilities), len(extraction.evidence),
        core_execution_provider, core_execution_model,
        " (Gemini 폴백)" if core_error is not None else "",
        responsibility_execution_provider, responsibility_execution_model,
        " (Gemini 폴백)" if responsibilities_error is not None else "",
    )
    return JobPostingExtractionResult(
        extraction=extraction,
        model_executions=[
            ModelExecution(
                stage=ModelExecutionStage.CORE_EXTRACTION,
                provider=core_execution_provider,
                model=core_execution_model,
            ),
            ModelExecution(
                stage=ModelExecutionStage.RESPONSIBILITY_EXTRACTION,
                provider=responsibility_execution_provider,
                model=responsibility_execution_model,
            ),
        ],
    )
