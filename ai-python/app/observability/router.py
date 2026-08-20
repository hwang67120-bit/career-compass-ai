"""관측성 지표 조회 엔드포인트(O2).

provider·stage별 호출수·에러율·P95 지연·누적 토큰을 한 번에 반환한다. 내부 서비스
토큰으로 보호하며(운영자·Java만 조회), 원문·개인정보는 담기지 않는다.
"""

from fastapi import APIRouter, Depends

from app.guardrails.internal_auth import verify_internal_token
from app.observability.metrics import metrics_snapshot

router = APIRouter(tags=["observability"])


@router.get("/internal/v1/metrics", dependencies=[Depends(verify_internal_token)])
async def get_metrics() -> dict[str, object]:
    """현재까지 집계된 LLM 호출 지표 스냅샷을 반환한다.

    응답 예:
        {"stages": {"ollama.extract_job_posting": {"calls": 12, "errors": 1,
        "errorRate": 0.0833, "p95LatencyMs": 7100.2, "avgLatencyMs": 6200.5,
        "promptTokensTotal": 15840, "completionTokensTotal": 2510}}}
    """
    return {"stages": metrics_snapshot()}
