"""채용공고 구조화 추출 모델을 같은 평가 텍스트 세트로 비교한다.

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

`contracts/job-posting-extraction.md` 8절이 재현한 문제 — 원문에 직무가
명확해도 모델이 `jobTitle`을 채우지 않는 경우가 있다 — 를
`evaluation/model_comparison.py`(이력서용)와 같은 방식(스키마 통과율, 근거
검증 통과율, 처리 시간)으로 비교한다. 다만 스키마·근거 검증을 통과해도
`jobTitle`만 비어 있는 경우를 놓치지 않도록 `job_title_missing` 결과
종류를 추가했다 — `tests/fixtures/job_postings/README.md`에 파일별로
직무명이 원문에서 근거 있게 명시돼 있는지를 정리해뒀다.

2026-08-03 수정: 같은 모델·같은 fixture를 `temperature: 0`으로 반복 호출해도
결과가 매번 같지 않다는 게 실제로 확인됐다(디코딩 비결정성 — GPU 배치 연산의
부동소수점 비결합성 때문으로 추정, `seed`는 아직 설정하지 않음). 1회 실행으로
통과율을 매기면 이 한 번의 흔들림이 그대로 통과율에 반영되므로, 같은 조합을
`REPEATS`회 반복해 fixture별 결과가 실행마다 달라지는지(`flaky`)를 함께
기록한다.

2026-08-04 수정: 세션 오염 완화책(근거 검증 실패 시 언로드 후 1회 재시도,
`app/services/job_posting_extraction.py`의 `_extract_core_with_retry`)이
실제로 통과율을 올리는지 이 스크립트가 지금까지 증명하지 못했다 —
`provider.extract_job_posting`을 직접 호출하고 재시도 로직을 안 탔기
때문이다. 이제 `_extract_core_with_retry`를 그대로 재사용해서(운영 코드
경로와 동일하게) 검증하고, 재시도가 실제로 몇 번 발동했는지도 집계한다.

같은 수정 과정에서 별개의 버그도 발견했다 — `provider.extract_job_posting`이
`JobPostingCoreExtraction`(담당 업무 필드 없음)을 반환하도록 이미 바뀌었는데
(2026-08-03 스키마 분리), 이 스크립트는 여전히 `filter_unevidenced_candidates`
(`JobPostingExtraction` 전용, `responsibilities` 속성을 직접 접근)에 그
결과를 그대로 넘기고 있었다 — `AttributeError`로 즉시 깨지는 상태였다.
직무명·기술만 담당 업무 없이 감싸서(`JobPostingExtraction`으로 변환) 넘기도록
고쳤다.

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.job_posting_model_comparison
"""

import asyncio
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from app.providers.ollama import OllamaProvider, OllamaResponseError, OllamaUnavailableError
from app.providers.settings import OllamaSettings
from app.schemas.job_posting import JobPostingExtraction
from app.services.job_posting_extraction import (
    JobPostingEvidenceValidationError,
    _extract_core_with_retry,
    filter_unevidenced_candidates,
)

FIXTURES_DIR = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "job_postings"

# 확인 필요: 최종 채택 모델이 아니라 비교 후보 목록이다(model_comparison.py와 같은 목록).
CANDIDATE_MODELS = [
    "qwen2.5:latest",
    "exaone3.5:latest",
    "llama3.2:latest",
]

# 같은 모델·같은 fixture를 몇 번 반복할지. 실행 시간과 비결정성 탐지 사이의
# 절충값이며 확인 필요 — 표본이 3회뿐이라 흔들리는 비율의 정밀한 추정치는 아니다.
REPEATS = 3

# 원문에 직무명 근거가 없어서(= jobTitle null이 정답인) job_title_missing 판정을
# 건너뛰어야 하는 fixture만 여기 나열한다. 나머지는 전부 "직무명 근거 있음"이
# 기본값이라, 새 fixture를 추가해도 이 목록을 안 고치면 KeyError가 나던 예전과
# 달리 조용히 기본값(True)으로 처리된다.
NO_JOB_TITLE_FIXTURES = {"no_job_title_stated.txt"}


@dataclass
class TrialResult:
    model: str
    fixture_name: str
    repeat_index: int
    outcome: str  # "success" | "schema_invalid" | "evidence_invalid" | "unavailable" | "job_title_missing"
    elapsed_seconds: float
    detail: str = ""
    retried: bool = False
    """세션 오염 완화책(언로드+재시도)이 이 시도에서 실제로 발동했는지."""


@dataclass
class FixtureSummary:
    fixture_name: str
    trials: list[TrialResult] = field(default_factory=list)

    @property
    def success_rate(self) -> float:
        if not self.trials:
            return 0.0
        return sum(1 for t in self.trials if t.outcome == "success") / len(self.trials)

    @property
    def is_flaky(self) -> bool:
        """같은 모델·같은 fixture인데 반복마다 결과 종류(outcome)가 갈리는지."""
        return len({t.outcome for t in self.trials}) > 1


@dataclass
class ModelSummary:
    model: str
    fixture_summaries: dict[str, FixtureSummary] = field(default_factory=dict)

    @property
    def trials(self) -> list[TrialResult]:
        return [t for summary in self.fixture_summaries.values() for t in summary.trials]

    @property
    def success_rate(self) -> float:
        trials = self.trials
        if not trials:
            return 0.0
        return sum(1 for t in trials if t.outcome == "success") / len(trials)

    @property
    def average_seconds(self) -> float:
        trials = self.trials
        if not trials:
            return 0.0
        return sum(t.elapsed_seconds for t in trials) / len(trials)

    @property
    def flaky_fixtures(self) -> list[FixtureSummary]:
        return [s for s in self.fixture_summaries.values() if s.is_flaky]


async def _run_trial(model: str, fixture_path: Path, repeat_index: int) -> TrialResult:
    settings = OllamaSettings()
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )

    source_text = fixture_path.read_text(encoding="utf-8")
    expects_job_title = fixture_path.name not in NO_JOB_TITLE_FIXTURES

    start = time.monotonic()
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"), timeout=timeout
    ) as client:
        provider = OllamaProvider(client=client, model_name=model)

        retried = False
        original_unload = provider.unload_model

        async def _counting_unload() -> None:
            nonlocal retried
            retried = True
            await original_unload()

        provider.unload_model = _counting_unload

        try:
            # 운영 코드와 같은 경로(언로드+1회 재시도 포함)를 그대로 탄다 —
            # 이 스크립트가 자체적으로 근거 검증을 다시 구현하지 않는다.
            candidate = await _extract_core_with_retry(source_text, provider)
        except OllamaUnavailableError as error:
            return TrialResult(
                model, fixture_path.name, repeat_index, "unavailable",
                time.monotonic() - start, str(error), retried,
            )
        except OllamaResponseError as error:
            return TrialResult(
                model, fixture_path.name, repeat_index, "schema_invalid",
                time.monotonic() - start, str(error), retried,
            )
        except JobPostingEvidenceValidationError as error:
            return TrialResult(
                model, fixture_path.name, repeat_index, "evidence_invalid",
                time.monotonic() - start, str(error), retried,
            )

    elapsed = time.monotonic() - start

    # filter_unevidenced_candidates는 담당 업무 필드가 있는 JobPostingExtraction
    # 전용이다 — 이 스크립트는 직무명·기술(core)만 비교하므로 담당 업무를 빈
    # 채로 감싸서 넘긴다(응답을 지어내는 게 아니라 애초에 이 스크립트가 담당
    # 업무를 요청하지 않았다는 뜻).
    full_shaped = JobPostingExtraction(
        evidence=candidate.evidence,
        job_title=candidate.job_title,
        job_title_evidence_ids=candidate.job_title_evidence_ids,
        required_skills=candidate.required_skills,
        preferred_skills=candidate.preferred_skills,
    )
    filtered = filter_unevidenced_candidates(full_shaped)

    if expects_job_title and filtered.job_title is None:
        return TrialResult(
            model,
            fixture_path.name,
            repeat_index,
            "job_title_missing",
            elapsed,
            "원문에 직무명 근거가 있는데도 jobTitle이 null로 반환됨",
            retried,
        )

    return TrialResult(model, fixture_path.name, repeat_index, "success", elapsed, retried=retried)


async def run_comparison(
    models: list[str],
    fixture_paths: list[Path],
    repeats: int = REPEATS,
    raw_log_path: Path | None = None,
) -> list[ModelSummary]:
    summaries = {model: ModelSummary(model=model) for model in models}
    raw_log = raw_log_path.open("w", encoding="utf-8") if raw_log_path else None
    try:
        for model in models:
            for fixture_path in fixture_paths:
                fixture_summary = FixtureSummary(fixture_name=fixture_path.name)
                summaries[model].fixture_summaries[fixture_path.name] = fixture_summary
                for repeat_index in range(repeats):
                    result = await _run_trial(model, fixture_path, repeat_index)
                    fixture_summary.trials.append(result)
                    retry_marker = " [재시도 발동]" if result.retried else ""
                    line = (
                        f"[{model}] {fixture_path.name} #{repeat_index + 1}/{repeats}: "
                        f"{result.outcome}{retry_marker} ({result.elapsed_seconds:.1f}s) {result.detail}"
                    )
                    print(line)
                    if raw_log:
                        raw_log.write(line + "\n")
                        raw_log.flush()
    finally:
        if raw_log:
            raw_log.close()
    return list(summaries.values())


def print_report(summaries: list[ModelSummary]) -> None:
    print("\n=== 모델 비교 결과 ===")
    print(f"{'모델':<20} {'통과율':>8} {'평균 시간':>10}")
    for summary in summaries:
        print(
            f"{summary.model:<20} {summary.success_rate:>7.0%} {summary.average_seconds:>9.1f}s"
        )

    print("\n=== 세션 오염 완화책(언로드+재시도) 실제 효과 ===")
    any_retried = False
    for summary in summaries:
        retried_trials = [t for t in summary.trials if t.retried]
        if not retried_trials:
            continue
        any_retried = True
        rescued = sum(1 for t in retried_trials if t.outcome == "success")
        print(
            f"  [{summary.model}] 재시도 발동 {len(retried_trials)}건 중 "
            f"{rescued}건 성공으로 복구, {len(retried_trials) - rescued}건은 재시도해도 실패 유지"
        )
    if not any_retried:
        print("  이번 실행에서는 재시도가 한 번도 발동하지 않았다(근거 검증 실패 없음).")

    print("\n=== 반복마다 결과가 갈린 fixture(비결정성) ===")
    any_flaky = False
    for summary in summaries:
        for fixture_summary in summary.flaky_fixtures:
            any_flaky = True
            outcomes = ", ".join(f"#{t.repeat_index + 1}={t.outcome}" for t in fixture_summary.trials)
            print(f"  [{summary.model}] {fixture_summary.fixture_name}: {outcomes}")
    if not any_flaky:
        print("  없음 — 모든 조합이 반복 내내 같은 결과였다.")

    print()
    for summary in summaries:
        failures = [t for t in summary.trials if t.outcome != "success"]
        if failures:
            print(f"-- {summary.model} 실패 상세 --")
            for trial in failures:
                print(f"  {trial.fixture_name} #{trial.repeat_index + 1}: {trial.outcome} — {trial.detail}")


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    fixture_paths = sorted(FIXTURES_DIR.glob("*.txt"))
    if not fixture_paths:
        raise SystemExit(f"평가용 채용공고 텍스트가 없습니다: {FIXTURES_DIR}")

    total_calls = len(fixture_paths) * len(CANDIDATE_MODELS) * REPEATS
    print(
        f"평가 텍스트 {len(fixture_paths)}개 x 후보 모델 {len(CANDIDATE_MODELS)}개 "
        f"x 반복 {REPEATS}회 = 총 {total_calls}회 호출"
    )
    raw_log_path = Path(__file__).resolve().parent / "job_posting_model_comparison_raw.log"
    summaries = await run_comparison(CANDIDATE_MODELS, fixture_paths, REPEATS, raw_log_path)
    print_report(summaries)


if __name__ == "__main__":
    asyncio.run(main())
