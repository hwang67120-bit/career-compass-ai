import pytest

from app.schemas.job_posting import JobPostingEvidence, JobPostingExtraction, JobPostingSkill
from app.services.job_posting_extraction import (
    JobPostingEvidenceValidationError,
    filter_unevidenced_candidates,
    validate_evidence,
)

SOURCE_TEXT = "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상."


def test_validate_evidence_rejects_hallucinated_source_text() -> None:
    payload = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="원문에 없는 문장",
            )
        ]
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_validate_evidence_rejects_dangling_reference() -> None:
    payload = JobPostingExtraction(
        required_skills=[JobPostingSkill(raw_name="Python", evidence_ids=["ghost"])],
    )

    with pytest.raises(JobPostingEvidenceValidationError):
        validate_evidence(payload, SOURCE_TEXT)


def test_filter_unevidenced_candidates_removes_skill_without_evidence() -> None:
    payload = JobPostingExtraction(
        evidence=[
            JobPostingEvidence(
                evidence_id="e1",
                field_path="requiredSkills[0].rawName",
                value="Python",
                source_text="Python 3년 이상",
            )
        ],
        required_skills=[
            JobPostingSkill(raw_name="Python", evidence_ids=["e1"]),
            JobPostingSkill(raw_name="Java", evidence_ids=[]),
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert [s.raw_name for s in filtered.required_skills] == ["Python"]
    assert [e.evidence_id for e in filtered.evidence] == ["e1"]


def test_filter_unevidenced_candidates_clears_job_title_without_evidence() -> None:
    payload = JobPostingExtraction(
        job_title="백엔드 개발자",
        job_title_evidence_ids=[],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert filtered.job_title is None
