"""각 처리 단계의 소요 시간을 측정해 로그로 남긴다.

계약(`contracts/document-extraction.md` 9절, `contracts/job-posting-extraction.md`
6절) 원칙 — 최적화 전에 모델 호출 시간과 전체 처리 시간을 먼저 측정한다.
로그에는 원문·개인정보·내부 토큰·모델 응답 전체를 남기지 않는다(같은 절).

기존 provider(`OllamaProvider` 등) 함수 시그니처는 바꾸지 않는다 — 호출부가
이 context manager로 감싸서 시간만 잰다. 결과는 Python 표준 `logging`으로만
남긴다(확인 필요 — 별도 저장소나 API 응답 포함 여부는 Java와 계약이 필요한
다음 단계다. 지금은 로그만으로 병목 여부를 확인하는 첫 단계다).
"""

import logging
import time
from collections.abc import Iterator
from contextlib import contextmanager

_logger = logging.getLogger("app.performance")


@contextmanager
def measure_stage(stage: str) -> Iterator[None]:
    """지정한 단계를 감싸 소요 시간을 재고 INFO 로그로 남긴다.

    입력:
        stage: 로그에 남길 단계 이름(예: `"ollama.extract_job_posting"`,
            `"github.fetch_tree"`). 원문·개인정보·모델 응답 내용이 아니라
            식별용 이름만 담아야 한다.

    사용 예:
        with measure_stage("ollama.extract_job_posting"):
            result = await provider.extract_job_posting(text)

    단계 중 예외가 발생해도 소요 시간은 그대로 로그에 남기고 예외를 다시 던진다.
    """
    start = time.perf_counter()
    try:
        yield
    finally:
        duration_ms = (time.perf_counter() - start) * 1000
        _logger.info("stage=%s duration_ms=%.1f", stage, duration_ms)
