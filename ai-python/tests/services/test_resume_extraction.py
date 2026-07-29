import pytest

from app.schemas.document import PageText
from app.schemas.profile_candidate import CandidateEvidence, CandidateSkill, ProfileCandidatePayload
from app.services.resume_extraction import (
    EvidenceValidationError,
    build_page_marked_text,
    validate_evidence,
)


def test_build_page_marked_text_marks_pages_and_removes_email() -> None:
    pages = [
        PageText(page_number=1, text="경력: 백엔드 개발자. 연락처: hong@example.com"),
        PageText(page_number=2, text="프로젝트: 결제 시스템 개발"),
    ]

    result = build_page_marked_text(pages)

    assert "[페이지 1]" in result
    assert "[페이지 2]" in result
    assert "hong@example.com" not in result
    assert "[EMAIL]" in result
    assert "프로젝트: 결제 시스템 개발" in result


def test_validate_evidence_passes_when_source_text_matches_page() -> None:
    pages = [PageText(page_number=1, text="3년간 백엔드 개발자로 근무했다.")]
    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="workExperiences[0].jobTitle",
                value="백엔드 개발자",
                source_text="3년간 백엔드 개발자로 근무했다.",
                page_number=1,
            )
        ]
    )

    validate_evidence(payload, pages)


def test_validate_evidence_rejects_hallucinated_source_text() -> None:
    pages = [PageText(page_number=1, text="3년간 백엔드 개발자로 근무했다.")]
    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="workExperiences[0].jobTitle",
                value="프론트엔드 개발자",
                source_text="원문에 없는 문장",
                page_number=1,
            )
        ]
    )

    with pytest.raises(EvidenceValidationError):
        validate_evidence(payload, pages)


def test_validate_evidence_rejects_unknown_page_number() -> None:
    pages = [PageText(page_number=1, text="3년간 백엔드 개발자로 근무했다.")]
    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="workExperiences[0].jobTitle",
                value="백엔드 개발자",
                source_text="3년간 백엔드 개발자로 근무했다.",
                page_number=2,
            )
        ]
    )

    with pytest.raises(EvidenceValidationError):
        validate_evidence(payload, pages)


def test_validate_evidence_allows_candidate_item_with_no_evidence() -> None:
    """제안(코덱스 확인 필요): 근거를 못 만든 항목은 빈 evidenceIds로 허용한다."""
    pages = [PageText(page_number=1, text="Java, Spring Boot 3년 경력")]
    payload = ProfileCandidatePayload(
        skills=[CandidateSkill(raw_name="Java", evidence_ids=[])],
    )

    validate_evidence(payload, pages)


def test_validate_evidence_still_rejects_dangling_evidence_reference() -> None:
    """근거가 없는 건 허용하지만, 존재하지 않는 근거를 참조하는 건 여전히 막는다."""
    pages = [PageText(page_number=1, text="Java, Spring Boot 3년 경력")]
    payload = ProfileCandidatePayload(
        skills=[CandidateSkill(raw_name="Java", evidence_ids=["ghost-id"])],
    )

    with pytest.raises(EvidenceValidationError):
        validate_evidence(payload, pages)
