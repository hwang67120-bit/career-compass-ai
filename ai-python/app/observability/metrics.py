"""LLM 호출 지표를 프로세스 메모리에 집계한다(관측성 O2).

O1(`app/services/performance_tracking.py`)이 호출마다 남기는 신호(소요 시간·성공/실패·
토큰)를 여기로 모아, provider·stage별 **호출수·에러율·P95 지연·누적 토큰**을 계산한다.
`GET /internal/v1/metrics`가 이 스냅샷을 반환한다.

토큰 개수·소요 시간 같은 수치만 담고 원문·개인정보는 저장하지 않는다. 값은 프로세스
메모리에만 있고 재시작하면 초기화된다(영구 저장·시계열 DB는 범위 밖).
"""

import threading
from collections import deque
from dataclasses import dataclass, field

# key(=provider.stage)당 최근 지연 표본 상한. P95 계산용이며 메모리 상한 역할도 한다.
_MAX_LATENCY_SAMPLES = 1000


@dataclass
class _StageStats:
    calls: int = 0
    errors: int = 0
    prompt_tokens_total: int = 0
    completion_tokens_total: int = 0
    latencies_ms: deque[float] = field(
        default_factory=lambda: deque(maxlen=_MAX_LATENCY_SAMPLES)
    )


def _percentile(sorted_values: list[float], q: float) -> float:
    """정렬된 값에서 분위수(q, 0~1)를 최근접 순위 방식으로 반환한다."""
    if not sorted_values:
        return 0.0
    index = min(len(sorted_values) - 1, int(q * len(sorted_values)))
    return sorted_values[index]


class MetricsRegistry:
    """provider·stage별 지표를 스레드 안전하게 집계한다."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._stats: dict[str, _StageStats] = {}

    def record(
        self,
        key: str,
        duration_ms: float,
        is_error: bool,
        prompt_tokens: int | None,
        completion_tokens: int | None,
    ) -> None:
        with self._lock:
            stats = self._stats.setdefault(key, _StageStats())
            stats.calls += 1
            if is_error:
                stats.errors += 1
            if prompt_tokens:
                stats.prompt_tokens_total += prompt_tokens
            if completion_tokens:
                stats.completion_tokens_total += completion_tokens
            stats.latencies_ms.append(duration_ms)

    def snapshot(self) -> dict[str, dict[str, object]]:
        with self._lock:
            result: dict[str, dict[str, object]] = {}
            for key, stats in self._stats.items():
                latencies = sorted(stats.latencies_ms)
                result[key] = {
                    "calls": stats.calls,
                    "errors": stats.errors,
                    "errorRate": round(stats.errors / stats.calls, 4)
                    if stats.calls
                    else 0.0,
                    "p95LatencyMs": round(_percentile(latencies, 0.95), 1)
                    if latencies
                    else None,
                    "avgLatencyMs": round(sum(latencies) / len(latencies), 1)
                    if latencies
                    else None,
                    "promptTokensTotal": stats.prompt_tokens_total,
                    "completionTokensTotal": stats.completion_tokens_total,
                }
            return result

    def reset(self) -> None:
        with self._lock:
            self._stats.clear()


_registry = MetricsRegistry()


def record_stage(
    key: str,
    duration_ms: float,
    is_error: bool,
    prompt_tokens: int | None,
    completion_tokens: int | None,
) -> None:
    """O1 계측이 호출마다 부른다(성공/실패·지연·토큰을 집계에 반영)."""
    _registry.record(key, duration_ms, is_error, prompt_tokens, completion_tokens)


def metrics_snapshot() -> dict[str, dict[str, object]]:
    """현재까지의 provider·stage별 집계 스냅샷을 반환한다."""
    return _registry.snapshot()


def reset_metrics() -> None:
    """집계를 초기화한다(테스트용)."""
    _registry.reset()
