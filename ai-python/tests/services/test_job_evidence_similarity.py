import pytest

from app.schemas.job_evidence_similarity import JudgeVerdict, SimilarityRequest
from app.services.job_evidence_similarity import compare_evidence


class FakeJudgeProvider:
    """judge_evidence_relation을 정해진 판정 목록으로 대신한다(네트워크 없음)."""

    def __init__(self, verdicts: list[JudgeVerdict]) -> None:
        self._verdicts = list(verdicts)
        self.model_name = "fake-judge"
        self.calls: list[tuple[str, list[tuple[str, str]]]] = []

    async def judge_evidence_relation(self, job_text, user_items):
        self.calls.append((job_text, user_items))
        return self._verdicts.pop(0)


def _request(job_evidence: list[dict], user_evidence: list[dict]) -> SimilarityRequest:
    return SimilarityRequest(
        comparisonTaskId="c1",
        jobAnalysisId="a1",
        jobPostingId="p1",
        jobEvidence=job_evidence,
        userEvidence=user_evidence,
    )


_JOB = {"evidenceId": "job-1", "category": "RESPONSIBILITY", "text": "백엔드 API 설계·운영"}
_USER_A = {
    "evidenceId": "user-a",
    "projectSourceId": "ps-1",
    "category": "PROJECT_RESPONSIBILITY",
    "text": "Redis 캐시로 API 지연을 줄임",
}
_USER_B = {
    "evidenceId": "user-b",
    "projectSourceId": "ps-2",
    "category": "PROJECT_RESPONSIBILITY",
    "text": "React로 대시보드 UI 구현",
}


@pytest.mark.asyncio
async def test_related_returns_best_match() -> None:
    provider = FakeJudgeProvider([JudgeVerdict(best_match_user_evidence_id="user-a", judgment="RELATED")])
    results, status = await compare_evidence(_request([_JOB], [_USER_A, _USER_B]), provider)

    assert status == "CALCULATED"
    assert results == [
        {
            "jobEvidenceId": "job-1",
            "status": "CALCULATED",
            "bestMatchUserEvidenceId": "user-a",
            "score": None,
            "judgment": "RELATED",
            "unavailableReason": None,
        }
    ]


@pytest.mark.asyncio
async def test_not_related_has_null_best_match() -> None:
    provider = FakeJudgeProvider([JudgeVerdict(best_match_user_evidence_id=None, judgment="NOT_RELATED")])
    results, status = await compare_evidence(_request([_JOB], [_USER_B]), provider)

    assert status == "CALCULATED"
    assert results[0]["judgment"] == "NOT_RELATED"
    assert results[0]["bestMatchUserEvidenceId"] is None


@pytest.mark.asyncio
async def test_no_user_evidence_is_not_calculable() -> None:
    second_job = {
        "evidenceId": "job-2",
        "category": "RESPONSIBILITY",
        "text": "배포 자동화와 운영",
    }
    provider = FakeJudgeProvider([])  # 호출되면 안 됨
    results, status = await compare_evidence(
        _request([_JOB, second_job], []),
        provider,
    )

    assert status == "NOT_CALCULABLE"
    assert [result["status"] for result in results] == [
        "NOT_CALCULABLE",
        "NOT_CALCULABLE",
    ]
    assert {result["unavailableReason"] for result in results} == {
        "COMPATIBLE_USER_EVIDENCE_MISSING"
    }
    assert provider.calls == []  # 사용자 근거 없으면 모델 호출하지 않는다


@pytest.mark.asyncio
async def test_invalid_best_match_id_is_nulled() -> None:
    provider = FakeJudgeProvider([JudgeVerdict(best_match_user_evidence_id="ghost", judgment="RELATED")])
    results, _ = await compare_evidence(_request([_JOB], [_USER_A]), provider)

    # 모델이 목록에 없는 id를 줘도 근거 유효성 방어로 null 처리
    assert results[0]["bestMatchUserEvidenceId"] is None
    assert results[0]["judgment"] == "RELATED"
