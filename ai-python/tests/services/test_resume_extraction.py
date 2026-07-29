import pytest

from app.schemas.document import PageText
from app.schemas.profile_candidate import CandidateEvidence, CandidateSkill, ProfileCandidatePayload
from app.services.resume_extraction import (
    EvidenceValidationError,
    build_page_marked_text,
    filter_unevidenced_candidates,
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


def test_validate_evidence_still_rejects_dangling_evidence_reference() -> None:
    """존재하지 않는 근거를 참조하는 항목은 여전히 계약 위반으로 막는다."""
    pages = [PageText(page_number=1, text="Java, Spring Boot 3년 경력")]
    payload = ProfileCandidatePayload(
        skills=[CandidateSkill(raw_name="Java", evidence_ids=["ghost-id"])],
    )

    with pytest.raises(EvidenceValidationError):
        validate_evidence(payload, pages)


def test_filter_unevidenced_candidates_removes_items_without_evidence() -> None:
    """계약 5절: 근거 없는 후보는 응답에서 제거한다(근거 없는 값을 사실처럼 반환하지 않음).

    동시에, 제거된 후보만 참조하던 근거(e2)도 응답에서 같이 빠져야 한다
    (최소 근거 원칙 — 아무도 참조하지 않는 근거를 남기지 않음).
    """
    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="skills[0].rawName",
                value="Java",
                source_text="Java 3년 경력",
                page_number=1,
            ),
            CandidateEvidence(
                evidence_id="e2",
                field_path="skills[1].rawName",
                value="Python",
                source_text="Python 사용 경험",
                page_number=1,
            ),
        ],
        skills=[
            CandidateSkill(raw_name="Java", evidence_ids=["e1"]),
            CandidateSkill(raw_name="Python", evidence_ids=[]),
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert [s.raw_name for s in filtered.skills] == ["Java"]
    assert [e.evidence_id for e in filtered.evidence] == ["e1"]


def test_filter_unevidenced_candidates_removes_evidence_orphaned_by_dropped_project() -> None:
    """근거가 있는 하위 기술이 있어도, 프로젝트 자체에 근거가 없으면 프로젝트
    전체가 제거되고, 그 기술만 참조하던 근거도 같이 제거돼야 한다."""
    from app.schemas.profile_candidate import CandidateProject

    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="projects[0].technologies[0].rawName",
                value="Java",
                source_text="Java로 개발",
                page_number=1,
            )
        ],
        projects=[
            CandidateProject(
                project_name="사내 툴",
                evidence_ids=[],  # 프로젝트 자체는 근거 없음 -> 통째로 제거 대상
                technologies=[CandidateSkill(raw_name="Java", evidence_ids=["e1"])],
            )
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert filtered.projects == []
    assert filtered.evidence == []


def test_filter_unevidenced_candidates_filters_project_technologies() -> None:
    """프로젝트 자체는 근거가 있어도, 근거 없는 개별 기술은 따로 제거한다."""
    from app.schemas.profile_candidate import CandidateProject

    payload = ProfileCandidatePayload(
        evidence=[
            CandidateEvidence(
                evidence_id="e1",
                field_path="projects[0].projectName",
                value="사내 툴",
                source_text="사내 툴 개발",
                page_number=1,
            )
        ],
        projects=[
            CandidateProject(
                project_name="사내 툴",
                evidence_ids=["e1"],
                technologies=[
                    CandidateSkill(raw_name="Java", evidence_ids=["e1"]),
                    CandidateSkill(raw_name="Python", evidence_ids=[]),
                ],
            )
        ],
    )

    filtered = filter_unevidenced_candidates(payload)

    assert len(filtered.projects) == 1
    assert [t.raw_name for t in filtered.projects[0].technologies] == ["Java"]
