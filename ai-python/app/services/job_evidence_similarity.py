"""공고 담당 업무와 사용자 프로젝트 업무를 LLM-as-judge로 의미 비교한다.

계약: contracts/job-evidence-similarity.md. Ollama가 기본 provider다.
공고 근거마다 사용자 근거 전체와 비교해 best-match와 RELATED/NOT_RELATED를 받는다.
"""

from app.providers.ollama import OllamaProvider
from app.schemas.job_evidence_similarity import SimilarityRequest


def _calculated(job_evidence_id: str, best_match_id: str | None, judgment: str) -> dict:
    return {
        "jobEvidenceId": job_evidence_id,
        "status": "CALCULATED",
        "bestMatchUserEvidenceId": best_match_id,
        "score": None,  # LLM_JUDGE는 보정 전 숫자 점수를 반환하지 않는다
        "judgment": judgment,
        "unavailableReason": None,
    }


def _not_calculable(job_evidence_id: str, reason: str) -> dict:
    return {
        "jobEvidenceId": job_evidence_id,
        "status": "NOT_CALCULABLE",
        "bestMatchUserEvidenceId": None,
        "score": None,
        "judgment": None,
        "unavailableReason": reason,
    }


async def compare_evidence(
    request: SimilarityRequest, provider: OllamaProvider
) -> tuple[list[dict], str]:
    """각 공고 근거를 사용자 근거 전체와 비교해 항목 결과와 전체 상태를 만든다.

    provider 예외(OllamaUnavailableError/OllamaResponseError)는 그대로 전달해
    라우터가 503/502로 변환한다.
    """
    user_items = [(user.evidence_id, user.text) for user in request.user_evidence]
    valid_ids = {evidence_id for evidence_id, _ in user_items}

    if not user_items:
        results = [
            _not_calculable(job.evidence_id, "COMPATIBLE_USER_EVIDENCE_MISSING")
            for job in request.job_evidence
        ]
        return results, "NOT_CALCULABLE"

    results: list[dict] = []
    for job in request.job_evidence:
        verdict = await provider.judge_evidence_relation(job.text, user_items)
        best_match_id = (
            verdict.best_match_user_evidence_id if verdict.judgment == "RELATED" else None
        )
        # 근거 유효성: 모델이 목록에 없는 id를 주면 무효로 본다.
        if best_match_id is not None and best_match_id not in valid_ids:
            best_match_id = None
        results.append(_calculated(job.evidence_id, best_match_id, verdict.judgment))

    return results, "CALCULATED"
