"""Ollama LLM-as-judge가 "채용공고 담당 업무 vs 사용자 프로젝트 업무"의
의미 관계를 RELATED/NOT_RELATED로 구분하는지 평가한다(스파이크, 2026-08-12).

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다. 계약
(`contracts/job-evidence-similarity.md`)은 "확정 전에는 DTO, enum, 저장 테이블과
Python endpoint를 구현하지 않는다"고 못박았으므로, 판정 스키마·프롬프트를
운영 코드(`app/providers/ollama.py`)에 넣지 않고 이 스크립트 안에 자체
정의한다 — `job_evidence_similarity_spike.py`(임베딩)와 같은 오프라인 전략이다.

왜 별도 평가인가:
- `job_evidence_similarity_spike.py`(임베딩, nomic-embed-text)는 품질 게이트
  실패였다 — 같은 백엔드 0.9948 < 무관한 프론트엔드 0.9980, Python–Java와
  Python–React가 모두 1.0000. Gemini 임베딩도 별도 결정으로 후보에서 빠졌다.
  그래서 `LLM_JUDGE`(Ollama)가 계약상 유일하게 남은 실행 가능 경로다
  (`contracts/job-evidence-similarity.md` 품질 게이트·"구현 전 확인 필요" 1번).
- 채용공고 구조화 추출 성능(`job_posting_model_comparison.py`)이 의미 비교
  성능을 보장하지 않는다 — 계약이 명시적으로 별도 평가를 요구한다. 그래서
  추출 비교와 같은 후보 목록(qwen2.5/exaone3.5/llama3.2)을 판정 과제로 다시 잰다.

무엇을 재는가(계약 `## 품질 게이트`를 판정 지표로 매핑):
- best-match 정확도(게이트 1·2): job 근거마다 사용자 프로젝트 후보 전체를 주고
  가장 관련 있는 하나를 고르게 한 뒤, 그 선택이 정답 도메인과 같은지 본다.
  같은 업무 쌍을 다른 업무 쌍보다 위로 올리는지를 "1/N 중 맞게 고르는가"로 잰다.
- RELATED/NOT_RELATED 분류 정확도(게이트 1): 정답에 대응 프로젝트가 없는 job은
  NOT_RELATED가 정답 — 판정이 이를 맞히는지 본다.
- 반복 안정성(게이트 3): 같은 job을 REPEATS회 반복해 판정·best-match가 갈리는지.
- 근거 유효성(게이트 4): 반환한 best-match id가 입력에 실제로 존재하는지.
- 지연(게이트 5): 호출당 소요 시간.

라벨링 주의: 정답이 모호한 job(예: ai_ml_engineer↔data_scientist 겹침,
fullstack↔backend/frontend 양쪽)은 `expected_related=None`으로 두어 정확도
집계에서 제외하고 결과만 따로 출력한다 — 모호한 쌍으로 모델을 불공정하게
깎지 않기 위해서다. 담당 업무 문장은 `tests/fixtures/job_postings/`의 17개
공고 원문에서 그대로 옮겼고, 사용자 프로젝트 근거는 이 스크립트에서 만든
합성 예시다(실제 시장 정확도가 아니라 도메인 구분력만 확인한다).

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.job_evidence_judge_spike
"""

import asyncio
import json
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal

import httpx
from pydantic import BaseModel, ValidationError

from app.providers.settings import OllamaSettings

# job_posting_model_comparison.py와 같은 후보 목록 — 추출 성능이 아니라 판정
# 성능을 이 후보들로 다시 잰다. 확인 필요: 최종 채택 모델이 아니라 비교 후보다.
CANDIDATE_MODELS = [
    "qwen2.5:latest",
    "exaone3.5:latest",
    "llama3.2:latest",
]

# 같은 모델·같은 job을 몇 번 반복할지(비결정성 탐지). 추출 비교와 같은 절충값.
REPEATS = 3


# 사용자 프로젝트 업무 근거(PROJECT_RESPONSIBILITY). 도메인마다 하나씩, 서로
# 명확히 구분되는 합성 예시다. job 근거는 이 후보 전체와 비교된다.
USER_EVIDENCE: dict[str, tuple[str, str]] = {
    # id: (도메인, 텍스트)
    "user-backend": (
        "backend",
        "Redis 캐시와 비동기 처리를 적용해 주문 API의 응답 지연을 줄였습니다.",
    ),
    "user-frontend": (
        "frontend",
        "React와 TypeScript로 관리자 대시보드 UI를 구현하고 반응형 레이아웃을 적용했습니다.",
    ),
    "user-data-eng": (
        "data-eng",
        "Airflow로 사용자 로그 수집 배치 파이프라인을 구축하고 Spark로 집계했습니다.",
    ),
    "user-data-science": (
        "data-science",
        "이탈 예측 모델을 scikit-learn으로 만들고 A/B 테스트로 개선 효과를 검증했습니다.",
    ),
    "user-mobile": (
        "mobile",
        "Kotlin과 Jetpack Compose로 커머스 앱 화면을 개발하고 코루틴으로 비동기 처리를 했습니다.",
    ),
    "user-security": (
        "security",
        "웹 애플리케이션의 OWASP Top 10 취약점을 점검하고 침해 로그를 분석했습니다.",
    ),
    "user-infra": (
        "infra",
        "Kubernetes와 Terraform으로 배포 파이프라인과 인프라 자동화를 구성했습니다.",
    ),
}


@dataclass(frozen=True)
class JobCase:
    """평가할 채용공고 담당 업무 근거 하나."""

    job_id: str
    fixture: str
    text: str
    # 정답 도메인 판정. expected_related:
    #   True  = USER_EVIDENCE에 같은 업무 프로젝트가 있음(best-match·RELATED 기대)
    #   False = 같은 업무 프로젝트가 없음(NOT_RELATED 기대)
    #   None  = 정답이 모호함(정확도 집계에서 제외, 결과만 출력)
    expected_related: bool | None
    # expected_related=True일 때 best-match로 인정할 사용자 근거 도메인 집합.
    expected_best_domains: frozenset[str] = frozenset()


# 담당 업무 문장은 tests/fixtures/job_postings/의 원문에서 그대로 옮겼다.
JOB_CASES: list[JobCase] = [
    JobCase("job-backend", "backend_java_spring.txt",
            "커머스 플랫폼의 주문·결제 백엔드 API 설계 및 운영",
            True, frozenset({"backend"})),
    JobCase("job-backend-title-sentence", "title_in_sentence_only.txt",
            "커머스 플랫폼의 주문·결제 API를 담당",
            True, frozenset({"backend"})),
    JobCase("job-backend-no-title", "no_job_title_stated.txt",
            "사내 결제 시스템의 정산 로직 설계와 API 서버 운영",
            True, frozenset({"backend"})),
    JobCase("job-backend-payment", "preferred_only_no_required.txt",
            "결제·정산 시스템의 백엔드 API 개발 및 운영",
            True, frozenset({"backend"})),
    JobCase("job-frontend", "frontend_react.txt",
            "대시보드 서비스의 웹 프론트엔드 UI 개발 및 유지보수",
            True, frozenset({"frontend"})),
    JobCase("job-frontend-mixed", "mixed_paragraph_requirements.txt",
            "React와 TypeScript를 활용해 여러 팀이 함께 쓰는 대시보드를 개발",
            True, frozenset({"frontend"})),
    JobCase("job-data-eng", "data_engineer.txt",
            "사용자 행동 로그 수집·적재 파이프라인 설계 및 운영",
            True, frozenset({"data-eng"})),
    JobCase("job-data-science", "data_scientist.txt",
            "사용자 이탈 예측 모델 개발 및 A/B 테스트 기반 서비스 개선",
            True, frozenset({"data-science"})),
    JobCase("job-mobile", "mobile_android.txt",
            "자사 커머스 앱의 Android 클라이언트 개발 및 유지보수",
            True, frozenset({"mobile"})),
    JobCase("job-security", "security_engineer.txt",
            "서비스 인프라 및 애플리케이션 보안 취약점 진단과 대응",
            True, frozenset({"security"})),
    JobCase("job-devops", "devops_infra.txt",
            "서비스 배포 파이프라인 구축 및 클라우드 인프라 운영",
            True, frozenset({"infra"})),
    JobCase("job-cloud", "cloud_platform_engineer.txt",
            "사내 서비스가 공통으로 쓰는 클라우드 플랫폼 설계 및 운영",
            True, frozenset({"infra"})),
    # 대응 프로젝트가 USER_EVIDENCE에 없음 → NOT_RELATED가 정답(명확한 음성).
    JobCase("job-game-server", "game_server_developer.txt",
            "모바일 게임의 실시간 매칭·전투 서버 개발",
            False),
    JobCase("job-qa", "qa_test_automation.txt",
            "웹·앱 서비스의 테스트 자동화 체계 구축 및 회귀 테스트 운영",
            False),
    # 정답 모호 → 집계 제외.
    #  - ai_ml: 추천 모델 학습이 data-science(이탈 예측 모델)와 겹친다.
    #  - fullstack: 백엔드+프론트엔드 양쪽이라 best-match 하나로 못 정한다.
    #  - llm_rag_backend: RAG 백엔드 API가 일반 backend API 개발과 겹친다.
    JobCase("job-ai-ml", "ai_ml_engineer.txt",
            "추천 모델 학습 파이프라인 개발과 모델 서빙 운영",
            None),
    JobCase("job-fullstack", "fullstack_node.txt",
            "내부 어드민 도구의 백엔드 API와 프론트엔드 화면 개발",
            None),
    JobCase("job-llm-rag", "llm_rag_backend.txt",
            "사내 문서 기반 RAG(검색 증강 생성) 서비스의 백엔드 API 개발",
            None),
]


class JudgeVerdict(BaseModel):
    """판정 구조화 출력. 계약 성공 응답의 항목 결과와 같은 모양이다 —
    점수·confidence 없이 best-match id와 RELATED/NOT_RELATED만."""

    best_match_user_evidence_id: str | None
    judgment: Literal["RELATED", "NOT_RELATED"]


_JUDGE_SYSTEM_PROMPT = (
    "너는 채용공고의 '담당 업무' 하나와 지원자의 '프로젝트 업무' 목록을 받는다. "
    "지원자 프로젝트 중 담당 업무와 의미상 같은 종류의 일을 하는 것을 판단한다. "
    "가장 관련 있는 프로젝트 하나를 bestMatchUserEvidenceId에 그 id로 담는다. "
    "그 프로젝트가 실제로 같은 종류의 업무면 judgment는 RELATED, 목록에 같은 종류의 "
    "업무가 하나도 없으면 judgment는 NOT_RELATED로 하고 bestMatchUserEvidenceId는 null로 둔다. "
    "id는 반드시 제공된 목록에 있는 값만 쓴다. 새 id·점수·설명을 만들지 않는다. "
    "기술 스택 이름이 같은지가 아니라 하는 일(업무)이 같은지로 판단한다."
)


@dataclass
class TrialResult:
    model: str
    job_id: str
    repeat_index: int
    outcome: str  # "ok" | "unavailable" | "schema_invalid"
    elapsed_seconds: float
    judgment: str | None = None
    best_match_id: str | None = None
    best_match_domain: str | None = None
    evidence_valid: bool = False  # best_match_id가 입력에 존재하는가(RELATED일 때)
    detail: str = ""


async def _judge(
    client: httpx.AsyncClient, model: str, job: JobCase
) -> tuple[JudgeVerdict | None, str]:
    """job 하나를 사용자 프로젝트 전체와 비교해 판정을 받는다.

    반환: (검증된 판정 또는 None, 실패 사유 문자열). None이면 사유가 채워진다.
    """
    schema = JudgeVerdict.model_json_schema()
    # id를 "id=..." 형식으로 주면 일부 모델(qwen2.5·exaone3.5)이 접두사째 복사해
    # "id=user-data-eng"를 반환하는 게 실제로 확인됐다(2026-08-12 1차 실행) —
    # 정답을 골랐는데도 id 조회가 어긋났다. 접두사 없는 형식으로 준다.
    user_lines = "\n".join(
        f"- {uid}: {text}" for uid, (_domain, text) in USER_EVIDENCE.items()
    )
    messages = [
        {"role": "system", "content": _JUDGE_SYSTEM_PROMPT},
        {
            "role": "user",
            "content": (
                f"JSON Schema: {json.dumps(schema, ensure_ascii=False)}"
                f"\n\n채용공고 담당 업무:\n{job.text}"
                f"\n\n지원자 프로젝트 업무 목록:\n{user_lines}"
            ),
        },
    ]

    try:
        response = await client.post(
            "/api/chat",
            json={
                "model": model,
                "stream": False,
                "format": schema,
                "options": {"temperature": 0},
                "messages": messages,
            },
        )
        response.raise_for_status()
        content = response.json()["message"]["content"]
        return JudgeVerdict.model_validate_json(content), ""
    except httpx.TimeoutException:
        return None, "unavailable: 응답 제한시간 초과"
    except httpx.HTTPError as error:
        return None, f"unavailable: {error}"
    except (KeyError, TypeError, ValueError, ValidationError) as error:
        return None, f"schema_invalid: {error}"


async def _run_trial(
    client: httpx.AsyncClient, model: str, job: JobCase, repeat_index: int
) -> TrialResult:
    start = time.monotonic()
    verdict, failure = await _judge(client, model, job)
    elapsed = time.monotonic() - start

    if verdict is None:
        outcome = "unavailable" if failure.startswith("unavailable") else "schema_invalid"
        return TrialResult(model, job.job_id, repeat_index, outcome, elapsed, detail=failure)

    best_id = verdict.best_match_user_evidence_id
    # 1차 실행에서 본 "id=" 접두사 복사에 대한 방어적 정규화(프롬프트 수정과 병행).
    if best_id is not None:
        best_id = best_id.removeprefix("id=").strip()
    best_domain = USER_EVIDENCE[best_id][0] if best_id in USER_EVIDENCE else None
    # RELATED면 best-match id가 입력에 존재해야 유효. NOT_RELATED면 null이 정상.
    if verdict.judgment == "RELATED":
        evidence_valid = best_id in USER_EVIDENCE
    else:
        evidence_valid = best_id is None or best_id in USER_EVIDENCE

    return TrialResult(
        model, job.job_id, repeat_index, "ok", elapsed,
        judgment=verdict.judgment,
        best_match_id=best_id,
        best_match_domain=best_domain,
        evidence_valid=evidence_valid,
    )


@dataclass
class ModelSummary:
    model: str
    trials: list[TrialResult] = field(default_factory=list)

    @property
    def ok_trials(self) -> list[TrialResult]:
        return [t for t in self.trials if t.outcome == "ok"]

    @property
    def average_seconds(self) -> float:
        ok = self.ok_trials
        return sum(t.elapsed_seconds for t in ok) / len(ok) if ok else 0.0


def _job_by_id() -> dict[str, JobCase]:
    return {job.job_id: job for job in JOB_CASES}


def _best_match_accuracy(summary: ModelSummary) -> tuple[int, int]:
    """expected_related=True이고 best 도메인이 정의된 job에서, best-match 도메인이
    정답 집합에 드는 비율. (정답 수, 대상 수)."""
    jobs = _job_by_id()
    correct = total = 0
    for trial in summary.ok_trials:
        job = jobs[trial.job_id]
        if job.expected_related is not True or not job.expected_best_domains:
            continue
        total += 1
        if trial.best_match_domain in job.expected_best_domains:
            correct += 1
    return correct, total


def _classification_accuracy(summary: ModelSummary) -> tuple[int, int]:
    """expected_related이 True/False로 명확한 job에서 RELATED/NOT_RELATED 분류
    정확도. (정답 수, 대상 수)."""
    jobs = _job_by_id()
    correct = total = 0
    for trial in summary.ok_trials:
        job = jobs[trial.job_id]
        if job.expected_related is None:
            continue
        total += 1
        predicted_related = trial.judgment == "RELATED"
        if predicted_related == job.expected_related:
            correct += 1
    return correct, total


def _flaky_jobs(summary: ModelSummary) -> list[tuple[str, str]]:
    """같은 job인데 반복마다 (judgment, best_match_id)가 갈린 것."""
    by_job: dict[str, list[TrialResult]] = {}
    for trial in summary.ok_trials:
        by_job.setdefault(trial.job_id, []).append(trial)
    flaky = []
    for job_id, trials in by_job.items():
        signatures = {(t.judgment, t.best_match_id) for t in trials}
        if len(signatures) > 1:
            detail = ", ".join(
                f"#{t.repeat_index + 1}={t.judgment}/{t.best_match_id}" for t in trials
            )
            flaky.append((job_id, detail))
    return flaky


async def run_comparison(
    models: list[str], jobs: list[JobCase], repeats: int, raw_log_path: Path | None
) -> list[ModelSummary]:
    settings = OllamaSettings()
    timeout = httpx.Timeout(
        connect=settings.ollama_connect_timeout_seconds,
        read=settings.ollama_read_timeout_seconds,
        write=10.0,
        pool=5.0,
    )
    summaries = [ModelSummary(model=model) for model in models]
    raw_log = raw_log_path.open("w", encoding="utf-8") if raw_log_path else None
    try:
        async with httpx.AsyncClient(
            base_url=str(settings.ollama_base_url).rstrip("/"), timeout=timeout
        ) as client:
            for summary in summaries:
                for job in jobs:
                    for repeat_index in range(repeats):
                        result = await _run_trial(client, summary.model, job, repeat_index)
                        summary.trials.append(result)
                        if result.outcome == "ok":
                            body = (
                                f"{result.judgment} best={result.best_match_id}"
                                f"({result.best_match_domain})"
                            )
                        else:
                            body = f"{result.outcome} — {result.detail}"
                        line = (
                            f"[{summary.model}] {job.job_id} #{repeat_index + 1}/{repeats}: "
                            f"{body} ({result.elapsed_seconds:.1f}s)"
                        )
                        print(line)
                        if raw_log:
                            raw_log.write(line + "\n")
                            raw_log.flush()
    finally:
        if raw_log:
            raw_log.close()
    return summaries


def print_report(summaries: list[ModelSummary]) -> None:
    jobs = _job_by_id()

    print("\n=== 모델별 판정 품질 ===")
    print(f"{'모델':<20} {'best-match':>12} {'RELATED분류':>12} {'근거유효':>10} {'평균시간':>10}")
    for summary in summaries:
        ok = summary.ok_trials
        if not ok:
            print(f"{summary.model:<20} {'(호출 실패)':>12}")
            continue
        bm_correct, bm_total = _best_match_accuracy(summary)
        cl_correct, cl_total = _classification_accuracy(summary)
        valid = sum(1 for t in ok if t.evidence_valid)
        bm = f"{bm_correct}/{bm_total}" if bm_total else "-"
        cl = f"{cl_correct}/{cl_total}" if cl_total else "-"
        print(
            f"{summary.model:<20} {bm:>12} {cl:>12} "
            f"{valid}/{len(ok):>8} {summary.average_seconds:>9.1f}s"
        )

    print("\n=== 반복마다 판정이 갈린 job(비결정성, 게이트 3) ===")
    any_flaky = False
    for summary in summaries:
        for job_id, detail in _flaky_jobs(summary):
            any_flaky = True
            print(f"  [{summary.model}] {job_id}: {detail}")
    if not any_flaky:
        print("  없음 — 모든 job이 반복 내내 같은 판정이었다.")

    print("\n=== best-match 오류·NOT_RELATED 상세 ===")
    for summary in summaries:
        ok = summary.ok_trials
        if not ok:
            continue
        wrong = []
        for trial in ok:
            job = jobs[trial.job_id]
            if job.expected_related is True and job.expected_best_domains:
                if trial.best_match_domain not in job.expected_best_domains:
                    wrong.append(
                        f"{trial.job_id} #{trial.repeat_index + 1}: "
                        f"{trial.judgment}/{trial.best_match_domain} "
                        f"(정답 {set(job.expected_best_domains)})"
                    )
            elif job.expected_related is False and trial.judgment != "NOT_RELATED":
                wrong.append(
                    f"{trial.job_id} #{trial.repeat_index + 1}: "
                    f"{trial.judgment}/{trial.best_match_domain} (정답 NOT_RELATED)"
                )
        if wrong:
            print(f"-- {summary.model} --")
            for line in wrong:
                print(f"  {line}")

    print("\n=== 정답 모호(집계 제외) job 판정 결과 ===")
    ambiguous_ids = [job.job_id for job in JOB_CASES if job.expected_related is None]
    for summary in summaries:
        ok = [t for t in summary.ok_trials if t.job_id in ambiguous_ids]
        if not ok:
            continue
        print(f"-- {summary.model} --")
        for trial in ok:
            print(
                f"  {trial.job_id} #{trial.repeat_index + 1}: "
                f"{trial.judgment}/{trial.best_match_domain}"
            )

    failures = [t for s in summaries for t in s.trials if t.outcome != "ok"]
    if failures:
        print("\n=== 호출 실패(unavailable/schema_invalid) ===")
        for trial in failures:
            print(f"  [{trial.model}] {trial.job_id} #{trial.repeat_index + 1}: {trial.detail}")


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    total_calls = len(JOB_CASES) * len(CANDIDATE_MODELS) * REPEATS
    print(
        f"job 근거 {len(JOB_CASES)}개 x 후보 모델 {len(CANDIDATE_MODELS)}개 "
        f"x 반복 {REPEATS}회 = 총 {total_calls}회 호출"
    )
    print(f"사용자 프로젝트 근거 {len(USER_EVIDENCE)}개(도메인별 1개)와 비교한다.")
    raw_log_path = Path(__file__).resolve().parent / "job_evidence_judge_spike_raw.log"
    summaries = await run_comparison(CANDIDATE_MODELS, JOB_CASES, REPEATS, raw_log_path)
    print_report(summaries)


if __name__ == "__main__":
    asyncio.run(main())
