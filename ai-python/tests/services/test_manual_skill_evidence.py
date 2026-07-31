from app.schemas.technical_evidence import EvidenceSource
from app.services.manual_skill_evidence import build_manual_skill_evidence


def test_build_manual_skill_evidence_creates_one_evidence_per_skill() -> None:
    result = build_manual_skill_evidence(["Python", "FastAPI"])

    skill_names = {skill.skill_name for skill in result.skills}
    assert skill_names == {"Python", "FastAPI"}
    assert len(result.evidence) == 2


def test_build_manual_skill_evidence_marks_source_as_manual_without_file_path() -> None:
    result = build_manual_skill_evidence(["Python"])

    evidence = result.evidence[0]
    assert evidence.evidence_source == EvidenceSource.MANUAL
    assert evidence.file_path is None


def test_build_manual_skill_evidence_deduplicates_case_insensitively() -> None:
    result = build_manual_skill_evidence(["Python", "python", " Python "])

    assert len(result.skills) == 1
    assert result.skills[0].skill_name == "Python"
    assert len(result.evidence) == 1


def test_build_manual_skill_evidence_ignores_blank_entries() -> None:
    result = build_manual_skill_evidence(["Python", "   ", ""])

    assert len(result.skills) == 1


def test_build_manual_skill_evidence_returns_empty_result_for_no_input() -> None:
    result = build_manual_skill_evidence([])

    assert result.skills == []
    assert result.evidence == []
