"""이력서 구조화 추출 모델을 같은 평가 PDF 세트로 비교한다.

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

로드맵(`docs/mvp-implementation-roadmap.md`) G단계: 모델 이름만으로 선택하지
않고 같은 평가 PDF에서 스키마 통과율, 근거 연결 오류율과 처리 시간을
비교해서 채택 모델을 정한다.

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.model_comparison
"""

import asyncio
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path

import httpx

from app.providers.ollama import OllamaProvider, OllamaResponseError, OllamaUnavailableError
from app.providers.settings import OllamaSettings
from app.services.pdf_extraction import extract_pdf_text
from app.services.resume_extraction import EvidenceValidationError, build_page_marked_text, validate_evidence

FIXTURES_DIR = Path(__file__).resolve().parent.parent / "tests" / "fixtures" / "resumes"

# 확인 필요: 최종 채택 모델이 아니라 비교 후보 목록이다.
CANDIDATE_MODELS = [
    "qwen2.5:latest",
    "exaone3.5:latest",
    "llama3.2:latest",
]


@dataclass
class TrialResult:
    model: str
    pdf_name: str
    outcome: str  # "success" | "schema_invalid" | "evidence_invalid" | "unavailable"
    elapsed_seconds: float
    detail: str = ""


@dataclass
class ModelSummary:
    model: str
    trials: list[TrialResult] = field(default_factory=list)

    @property
    def success_rate(self) -> float:
        if not self.trials:
            return 0.0
        return sum(1 for t in self.trials if t.outcome == "success") / len(self.trials)

    @property
    def average_seconds(self) -> float:
        if not self.trials:
            return 0.0
        return sum(t.elapsed_seconds for t in self.trials) / len(self.trials)


async def _run_trial(model: str, pdf_path: Path) -> TrialResult:
    settings = OllamaSettings()
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )

    pages = extract_pdf_text(pdf_path.read_bytes())
    page_marked_text = build_page_marked_text(pages)

    start = time.monotonic()
    async with httpx.AsyncClient(
        base_url=str(settings.ollama_base_url).rstrip("/"), timeout=timeout
    ) as client:
        provider = OllamaProvider(client=client, model_name=model)
        try:
            candidate = await provider.extract_resume_profile(page_marked_text)
        except OllamaUnavailableError as error:
            return TrialResult(model, pdf_path.name, "unavailable", time.monotonic() - start, str(error))
        except OllamaResponseError as error:
            return TrialResult(model, pdf_path.name, "schema_invalid", time.monotonic() - start, str(error))

    elapsed = time.monotonic() - start
    try:
        validate_evidence(candidate, pages)
    except EvidenceValidationError as error:
        return TrialResult(model, pdf_path.name, "evidence_invalid", elapsed, str(error))

    return TrialResult(model, pdf_path.name, "success", elapsed)


async def run_comparison(
    models: list[str], pdf_paths: list[Path], raw_log_path: Path | None = None
) -> list[ModelSummary]:
    summaries = {model: ModelSummary(model=model) for model in models}
    raw_log = raw_log_path.open("w", encoding="utf-8") if raw_log_path else None
    try:
        for model in models:
            for pdf_path in pdf_paths:
                result = await _run_trial(model, pdf_path)
                summaries[model].trials.append(result)
                line = f"[{model}] {pdf_path.name}: {result.outcome} ({result.elapsed_seconds:.1f}s) {result.detail}"
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
    print()
    for summary in summaries:
        failures = [t for t in summary.trials if t.outcome != "success"]
        if failures:
            print(f"-- {summary.model} 실패 상세 --")
            for trial in failures:
                print(f"  {trial.pdf_name}: {trial.outcome} — {trial.detail}")


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    pdf_paths = sorted(FIXTURES_DIR.glob("*.pdf"))
    if not pdf_paths:
        raise SystemExit(f"평가용 PDF가 없습니다: {FIXTURES_DIR}")

    print(f"평가 PDF {len(pdf_paths)}개 x 후보 모델 {len(CANDIDATE_MODELS)}개")
    raw_log_path = Path(__file__).resolve().parent / "model_comparison_raw.log"
    summaries = await run_comparison(CANDIDATE_MODELS, pdf_paths, raw_log_path)
    print_report(summaries)


if __name__ == "__main__":
    asyncio.run(main())
