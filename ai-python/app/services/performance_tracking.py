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

import logging
import time
from collections.abc import Iterator
from contextlib import contextmanager
from enum import Enum

_logger = logging.getLogger("app.performance")


class StageOperation(str, Enum):
    """계측을 허용하는 작업 이름이다. 호출부는 이 목록에서만 골라야 한다."""

    EXTRACT_JOB_POSTING = "extract_job_posting"
    EXTRACT_RESUME_PROFILE = "extract_resume_profile"
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
    start = time.perf_counter()
    try:
        yield
    finally:
        duration_ms = (time.perf_counter() - start) * 1000
        _logger.info("stage=%s.%s duration_ms=%.1f", component, operation.value, duration_ms)
