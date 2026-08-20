"""Ollama judge 평가(`job_evidence_judge_spike.py`)와 같은 데이터셋·프롬프트·채점으로
Gemini 폴백 경로의 판정 품질을 교차 검증한다(2026-08-12).

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

왜 별도 스크립트인가:
- 계약(`contracts/job-evidence-similarity.md`)은 Ollama를 기본, Gemini를 폴백·제한적
  교차검증으로 쓴다. `job-fit-semantic-similarity.md`의 3단계("Ollama 실패 시 Gemini
  폴백이 같은 계약을 지키는지 제한적으로 확인")가 이 스크립트다.
- Gemini는 구조화 출력을 `response_json_schema` config로 받고 무료 등급 분당 요청
  한도가 있어(Ollama엔 없는 제약) 추출 교차검증(`job_posting_gemini_check.py`)과
  같은 방식으로 별도 스크립트에 속도 제한을 두고 분리한다.

무엇을 공유하는가: 판정 데이터셋(JOB_CASES/USER_EVIDENCE), 의미 프롬프트
(`judge_user_content`), 판정 스키마(`JudgeVerdict`), 채점 규칙(`verdict_to_trial`)과
리포트(`print_report`)를 `job_evidence_judge_spike.py`에서 그대로 import한다 —
"같은 방식"을 코드로 보장하고 중복을 피한다. Gemini 쪽은 호출 방법과 속도 제한만 다르다.

2026-08-12 1차 실행 결과(REPEATS=3, 51회 시도): 성공한 21회는 전부 정답이었으나
(best-match 20/20, 분류 21/21, 근거유효 21/21), 무료 등급 "하루 20회" 한도에 막혀
나머지 30회가 429였다. 커버된 도메인이 backend/frontend에 편중되고 security·mobile·
infra·game-server(핵심 NOT_RELATED) 등은 한 번도 평가되지 못해 판정 능력을 "통과"로
결론낼 수 없다 — 이 하루 20회 한도 자체가 Gemini를 폴백·제한적 교차검증으로만 쓰는
계약 결정을 뒷받침한다. 전체 도메인 1회 커버가 필요하면 REPEATS=1로 재실행한다.

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.job_evidence_judge_gemini_check
"""

import asyncio
import sys
import time
from pathlib import Path

import httpx
from google import genai
from google.genai import errors, types
from pydantic import ValidationError

from app.providers.settings import GeminiSettings
from evaluation.job_evidence_judge_spike import (
    _JUDGE_SYSTEM_PROMPT,
    JOB_CASES,
    JobCase,
    JudgeVerdict,
    ModelSummary,
    TrialResult,
    judge_user_content,
    print_report,
    verdict_to_trial,
)

# 무료 등급에서 실측한 실제 벽은 "하루" 20회다(아래 RATE_LIMIT 주석 참고). 전체를
# 다 돌리려면 REPEATS x 17 <= 20이어야 해서 무료 등급에서는 REPEATS=1(17회)만 가능하다.
# REPEATS=3(51회)은 유료 등급이나 여러 날에 나눠 실행할 때만 완주한다.
REPEATS = 3

# 2026-08-12 실측: 무료 등급 제한은 "분당"이 아니라 "하루" 20회다
# (generativelanguage.googleapis.com/generate_content_free_tier_requests,
# GenerateRequestsPerDayPerProjectPerModel-FreeTier, limit 20, gemini-3.6-flash).
# 호출 간 간격은 이 일일 한도를 못 피한다 — 21회 성공 후 나머지가 전부 429였다.
# 이 간격은 일일 한도가 상향됐을 때의 분당 버스트 방지용으로만 남겨둔다.
RATE_LIMIT_DELAY_SECONDS = 20


async def _judge_gemini(
    client: genai.Client, model: str, job: JobCase
) -> tuple[JudgeVerdict | None, str]:
    """job 하나를 사용자 프로젝트 전체와 비교해 Gemini 판정을 받는다.

    반환: (검증된 판정 또는 None, 실패 사유). Ollama `_judge`와 같은 계약이지만
    스키마를 response_json_schema config로 전달하고 예외 종류가 다르다.
    """
    try:
        response = await client.aio.models.generate_content(
            model=model,
            contents=judge_user_content(job),
            config=types.GenerateContentConfig(
                system_instruction=_JUDGE_SYSTEM_PROMPT,
                response_mime_type="application/json",
                response_json_schema=JudgeVerdict.model_json_schema(),
                temperature=0,
            ),
        )
    except errors.ClientError as error:
        # 429(RESOURCE_EXHAUSTED) 등 요청 거부. 속도 제한이 여기로 온다.
        return None, f"client_error: {error}"
    except errors.ServerError as error:
        return None, f"unavailable: {error}"
    except httpx.HTTPError as error:
        return None, f"unavailable: {error}"

    if response.text is None:
        return None, "schema_invalid: 빈 응답"
    try:
        return JudgeVerdict.model_validate_json(response.text), ""
    except ValidationError as error:
        return None, f"schema_invalid: {error}"


async def _run_trial_gemini(
    client: genai.Client, model: str, job: JobCase, repeat_index: int
) -> TrialResult:
    start = time.monotonic()
    verdict, failure = await _judge_gemini(client, model, job)
    elapsed = time.monotonic() - start

    if verdict is None:
        if failure.startswith("unavailable"):
            outcome = "unavailable"
        elif failure.startswith("client_error"):
            outcome = "rate_limited"
        else:
            outcome = "schema_invalid"
        return TrialResult(model, job.job_id, repeat_index, outcome, elapsed, detail=failure)

    return verdict_to_trial(model, job, repeat_index, verdict, elapsed)


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    settings = GeminiSettings()
    model = settings.gemini_model

    total_calls = len(JOB_CASES) * REPEATS
    print(
        f"[gemini:{model}] job 근거 {len(JOB_CASES)}개 x 반복 {REPEATS}회 = 총 {total_calls}회 호출"
    )
    print(
        f"호출 사이 {RATE_LIMIT_DELAY_SECONDS}초 대기 — 총 예상 시간 약 "
        f"{total_calls * RATE_LIMIT_DELAY_SECONDS / 60:.0f}분"
    )

    raw_log_path = Path(__file__).resolve().parent / "job_evidence_judge_gemini_check_raw.log"
    raw_log = raw_log_path.open("w", encoding="utf-8")

    client = genai.Client(api_key=settings.gemini_api_key)
    summary = ModelSummary(model=model)
    is_first_call = True
    try:
        for job in JOB_CASES:
            for repeat_index in range(REPEATS):
                if not is_first_call:
                    await asyncio.sleep(RATE_LIMIT_DELAY_SECONDS)
                is_first_call = False

                result = await _run_trial_gemini(client, model, job, repeat_index)
                summary.trials.append(result)

                if result.outcome == "ok":
                    body = f"{result.judgment} best={result.best_match_id}({result.best_match_domain})"
                else:
                    quota = " [속도제한 의심]" if result.elapsed_seconds < 1.0 else ""
                    body = f"{result.outcome} — {result.detail}{quota}"
                line = (
                    f"[{model}] {job.job_id} #{repeat_index + 1}/{REPEATS}: "
                    f"{body} ({result.elapsed_seconds:.1f}s)"
                )
                print(line)
                raw_log.write(line + "\n")
                raw_log.flush()
    finally:
        raw_log.close()
        client.close()

    print_report([summary])


if __name__ == "__main__":
    asyncio.run(main())
