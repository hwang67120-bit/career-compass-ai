"""Python 개별 호출부의 처리시간을 기초 계측한다.

측정하는 건 소요 시간뿐이다 — 정확도·토큰·분류 성능은 다루지 않는다
(2026-08-01 리뷰 반영, 이름·설명 모두 이에 맞게 정리).

계약(`contracts/document-extraction.md` 9절, `contracts/job-posting-extraction.md`
6절)이 요구하는 전체 구간 중 지금은 일부만 계측한다 — 모델(LLM·임베딩) 호출과
GitHub 조회 호출부. 아직 없는 것(확인 필요, 다음 작업):

- PDF 텍스트 추출 시간
- 개인정보 제거 시간
- Python 내부 API 전체 처리 시간
- Java에서 측정하는 Python 왕복 시간
- 저장소·공고 하나의 전체 분석 시간, 성공·부분 완료·실패 결과
- 여러 요청이 동시에 들어올 때 로그를 구분할 요청 식별자(`requestId` 등) —
  Java 분석 API가 연결된 뒤에 추가한다.
- 성공/실패 결과(outcome) 구분 — 지금은 예외가 나도 소요 시간만 같은 형식으로
  로그에 남는다.

로그에는 원문·개인정보·내부 토큰·모델 응답 전체를 남기지 않는다. 기존
provider(`OllamaProvider` 등) 함수 시그니처는 바꾸지 않는다 — 호출부가
이 context manager로 감싸서 시간만 잰다.

단계 이름은 자유 문자열을 받지 않는다 — `component`(provider 이름 등
호출부를 식별하는 문자열)와 `StageOperation`(허용된 작업 이름만 담은
열거형)을 조합해서만 만든다. 원문이 실수로 로그에 섞여 들어가는 걸 막는다.
"""

import contextvars
import logging
import time
from collections.abc import Iterator
from contextlib import contextmanager
from enum import Enum

from app.observability.metrics import record_stage

_logger = logging.getLogger("app.performance")

# 요청 단위 식별자와 직전 모델 호출의 토큰 사용량을 계측에 연결한다. provider 함수
# 시그니처를 바꾸지 않으려고 contextvar로 전달한다(같은 async 컨텍스트에서 전파됨).
_request_id_var: contextvars.ContextVar[str | None] = contextvars.ContextVar(
    "performance_request_id", default=None
)
_usage_var: contextvars.ContextVar[tuple[int | None, int | None] | None] = (
    contextvars.ContextVar("performance_model_usage", default=None)
)


def set_request_id(request_id: str | None) -> None:
    """요청 식별자를 계측 로그에 연결한다(라우터가 요청 시작 시 호출)."""
    _request_id_var.set(request_id)


def set_last_usage(prompt_tokens: int | None, completion_tokens: int | None) -> None:
    """직전 모델 호출의 토큰 사용량을 기록한다(provider가 응답을 읽을 때 호출).

    현재 `measure_stage` 블록이 finally에서 이 값을 수거해 로그로 남기고 비운다.
    토큰 개수 외의 응답 내용(원문·개인정보)은 저장하지 않는다.
    """
    _usage_var.set((prompt_tokens, completion_tokens))


class StageOperation(str, Enum):
    """계측을 허용하는 작업 이름이다. 호출부는 이 목록에서만 골라야 한다."""

    EXTRACT_JOB_POSTING = "extract_job_posting"
    EXTRACT_JOB_POSTING_RESPONSIBILITIES = "extract_job_posting_responsibilities"
    GENERATE_JOB_SEARCH_KEYWORDS = "generate_job_search_keyword_suggestions"
    EMBED_USER_PROFILE = "embed_user_profile"
    EMBED_JOB_POSTING = "embed_job_posting"
    MATCH_SKILL_TAG = "match_skill_tag"
    GITHUB_FETCH_TREE = "fetch_tree"
    GITHUB_FETCH_MANIFEST_FILES = "fetch_manifest_files"
    GITHUB_FETCH_README_FILES = "fetch_readme_files"


@contextmanager
def measure_stage(component: str, operation: StageOperation) -> Iterator[None]:
    """지정한 단계를 감싸 소요 시간을 재고 INFO 로그로 남긴다.

    입력:
        component: 호출부를 식별하는 이름(예: `"ollama"`, `"gemini"`, `"github"`).
            provider 이름처럼 짧은 식별자만 담아야 한다 — 원문이나 사용자
            입력을 그대로 넘기지 않는다.
        operation: `StageOperation` 중 하나. 자유 문자열은 받지 않는다.

    사용 예:
        with measure_stage(provider.provider_name, StageOperation.EXTRACT_JOB_POSTING):
            result = await provider.extract_job_posting(text)

    단계 중 예외가 발생해도 소요 시간은 그대로 로그에 남기고 예외를 다시 던진다.
    지금은 성공·실패를 구분하지 않는다(확인 필요 — 위 모듈 docstring 참고).
    """
    _usage_var.set(None)  # 이전 호출의 잔여 사용량이 이 단계에 섞이지 않게 초기화
    start = time.perf_counter()
    outcome = "success"
    error_type = "none"
    try:
        yield
    except Exception as error:  # 결과만 기록하고 그대로 다시 던진다
        outcome = "error"
        error_type = type(error).__name__
        raise
    finally:
        duration_ms = (time.perf_counter() - start) * 1000
        usage = _usage_var.get()
        prompt_tokens = usage[0] if usage else None
        completion_tokens = usage[1] if usage else None
        _logger.info(
            "stage=%s.%s duration_ms=%.1f outcome=%s errorType=%s "
            "requestId=%s promptTokens=%s completionTokens=%s",
            component,
            operation.value,
            duration_ms,
            outcome,
            error_type,
            _request_id_var.get() or "none",
            prompt_tokens if prompt_tokens is not None else "none",
            completion_tokens if completion_tokens is not None else "none",
        )
        record_stage(
            f"{component}.{operation.value}",
            duration_ms,
            outcome == "error",
            prompt_tokens,
            completion_tokens,
        )
        _usage_var.set(None)
