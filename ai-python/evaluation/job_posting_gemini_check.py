"""Ollama 비교 평가(`job_posting_model_comparison.py`)와 같은 fixture·판정 기준으로
Gemini를 교차 검증한다.

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

`job_posting_model_comparison.py`는 Ollama 후보 모델만 비교하고 Gemini는
포함하지 않았다 — 2026-08-04 모듈 docstring에 "같은 fixture를 Gemini로
교차 검증하면 매번 깨끗하게 통과했다"는 기록이 있지만 그건 수동 1회 확인이었고,
반복 가능한 평가 스크립트에는 없었다. 이 스크립트가 그 공백을 채운다.

Gemini는 Ollama와 달리 core·담당 업무를 한 번의 호출(`extract_job_posting`)로
같이 반환하고, 세션 오염·언로드·재시도 개념이 없다 — 그래서 별도 스크립트로
분리했다(공유 리팩터링은 하지 않음, 확인 필요 시 다음 작업).

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.job_posting_gemini_check
"""

import asyncio
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

from google import genai

from app.providers.gemini import GeminiProvider, GeminiResponseError, GeminiUnavailableError
from app.providers.settings import GeminiSettings
from app.services.job_posting_extraction import (
    JobPostingEvidenceValidationError,
    filter_unevidenced_candidates,
    validate_evidence,
)

FIXTURES_DIR = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "job_postings"

REPEATS = 3

# Gemini 무료 등급 분당 요청 한도(실제 확인: generate_content_free_tier_requests
# 분당 5~20회)를 넘기지 않으려고 호출 사이에 두는 간격이다. 2026-08-11에 간격 없이
# 51회를 연달아 호출해서 대부분 429(RESOURCE_EXHAUSTED)로 막힌 걸 실제로 확인했다 —
# 그때 나온 41% 통과율은 품질 지표가 아니라 속도 제한 때문에 나온 숫자였다.
RATE_LIMIT_DELAY_SECONDS = 20

# job_posting_model_comparison.py와 같은 목록 — 직무명 근거가 원문에 없어서
# jobTitle이 null인 게 정답인 fixture만 여기 나열한다.
NO_JOB_TITLE_FIXTURES = {"no_job_title_stated.txt"}


@dataclass
class TrialResult:
    fixture_name: str
    repeat_index: int
    outcome: str  # "success" | "schema_invalid" | "evidence_invalid" | "unavailable" | "job_title_missing"
    elapsed_seconds: float
    detail: str = ""


@dataclass
class FixtureSummary:
    fixture_name: str
    trials: list[TrialResult] = field(default_factory=list)

    @property
    def is_flaky(self) -> bool:
        return len({t.outcome for t in self.trials}) > 1


async def _run_trial(provider: GeminiProvider, fixture_path: Path, repeat_index: int) -> TrialResult:
    source_text = fixture_path.read_text(encoding="utf-8")
    expects_job_title = fixture_path.name not in NO_JOB_TITLE_FIXTURES

    start = time.monotonic()
    try:
        candidate = await provider.extract_job_posting(source_text)
        validate_evidence(candidate, source_text)
    except GeminiUnavailableError as error:
        return TrialResult(fixture_path.name, repeat_index, "unavailable", time.monotonic() - start, str(error))
    except GeminiResponseError as error:
        return TrialResult(
            fixture_path.name, repeat_index, "schema_invalid", time.monotonic() - start, str(error)
        )
    except JobPostingEvidenceValidationError as error:
        return TrialResult(
            fixture_path.name, repeat_index, "evidence_invalid", time.monotonic() - start, str(error)
        )

    elapsed = time.monotonic() - start
    filtered = filter_unevidenced_candidates(candidate)

    if expects_job_title and filtered.job_title is None:
        return TrialResult(
            fixture_path.name, repeat_index, "job_title_missing", elapsed,
            "원문에 직무명 근거가 있는데도 jobTitle이 null로 반환됨",
        )

    return TrialResult(fixture_path.name, repeat_index, "success", elapsed)


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    settings = GeminiSettings()
    fixture_paths = sorted(FIXTURES_DIR.glob("*.txt"))
    if not fixture_paths:
        raise SystemExit(f"평가용 채용공고 텍스트가 없습니다: {FIXTURES_DIR}")

    total_calls = len(fixture_paths) * REPEATS
    print(f"[gemini:{settings.gemini_model}] 평가 텍스트 {len(fixture_paths)}개 x 반복 {REPEATS}회 = 총 {total_calls}회 호출")

    print(f"호출 사이 {RATE_LIMIT_DELAY_SECONDS}초씩 대기 — 총 예상 시간 약 {total_calls * RATE_LIMIT_DELAY_SECONDS / 60:.0f}분")

    client = genai.Client(api_key=settings.gemini_api_key)
    provider = GeminiProvider(client=client, model_name=settings.gemini_model)
    summaries: dict[str, FixtureSummary] = {}
    is_first_call = True
    try:
        for fixture_path in fixture_paths:
            summary = FixtureSummary(fixture_name=fixture_path.name)
            summaries[fixture_path.name] = summary
            for repeat_index in range(REPEATS):
                if not is_first_call:
                    await asyncio.sleep(RATE_LIMIT_DELAY_SECONDS)
                is_first_call = False
                result = await _run_trial(provider, fixture_path, repeat_index)
                summary.trials.append(result)
                quota_suspect = " [속도제한 의심 — 응답이 너무 빠름]" if result.outcome != "success" and result.elapsed_seconds < 1.0 else ""
                print(
                    f"[gemini] {fixture_path.name} #{repeat_index + 1}/{REPEATS}: "
                    f"{result.outcome} ({result.elapsed_seconds:.1f}s) {result.detail}{quota_suspect}"
                )
    finally:
        client.close()

    all_trials = [t for summary in summaries.values() for t in summary.trials]
    success_rate = sum(1 for t in all_trials if t.outcome == "success") / len(all_trials)
    average_seconds = sum(t.elapsed_seconds for t in all_trials) / len(all_trials)

    print("\n=== Gemini 결과 ===")
    print(f"통과율 {success_rate:.0%}, 평균 시간 {average_seconds:.1f}s")

    print("\n=== 반복마다 결과가 갈린 fixture(비결정성) ===")
    any_flaky = False
    for summary in summaries.values():
        if summary.is_flaky:
            any_flaky = True
            outcomes = ", ".join(f"#{t.repeat_index + 1}={t.outcome}" for t in summary.trials)
            print(f"  {summary.fixture_name}: {outcomes}")
    if not any_flaky:
        print("  없음 — 모든 fixture가 반복 내내 같은 결과였다.")

    failures = [t for t in all_trials if t.outcome != "success"]
    if failures:
        print("\n-- 실패 상세 --")
        for trial in failures:
            print(f"  {trial.fixture_name} #{trial.repeat_index + 1}: {trial.outcome} — {trial.detail}")


if __name__ == "__main__":
    asyncio.run(main())
