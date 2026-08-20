from app.schemas.technical_evidence import EvidenceSource
from app.services.manual_skill_evidence import build_manual_skill_evidence
from app.services.repository_evidence import extract_repository_evidence
from app.services.technical_profile import merge_technical_evidence


def test_merge_keeps_both_sources_as_separate_evidence_but_combines_matching_skill() -> None:
    repository_result = extract_repository_evidence(
        tree_paths=["backend/build.gradle"],
        manifest_contents={
            "backend/build.gradle": "implementation 'org.springframework.boot:spring-boot-starter-web'"
        },
    )
    manual_result = build_manual_skill_evidence(["Spring Boot", "Kubernetes"])

    merged = merge_technical_evidence(repository_result, manual_result)

    spring_boot = next(skill for skill in merged.skills if skill.skill_name == "Spring Boot")
    assert len(spring_boot.evidence_ids) == 2

    evidence_by_id = {evidence.evidence_id: evidence for evidence in merged.evidence}
    sources = {evidence_by_id[evidence_id].evidence_source for evidence_id in spring_boot.evidence_ids}
    assert sources == {EvidenceSource.REPOSITORY, EvidenceSource.MANUAL}

    kubernetes = next(skill for skill in merged.skills if skill.skill_name == "Kubernetes")
    assert len(kubernetes.evidence_ids) == 1


def test_merge_does_not_lose_or_duplicate_evidence() -> None:
    repository_result = extract_repository_evidence(
        tree_paths=["app.py"], manifest_contents={}
    )
    manual_result = build_manual_skill_evidence(["Python", "SQL"])

    merged = merge_technical_evidence(repository_result, manual_result)

    assert len(merged.evidence) == len(repository_result.evidence) + len(manual_result.evidence)


def test_merge_with_single_extraction_returns_equivalent_result() -> None:
    manual_result = build_manual_skill_evidence(["Go"])

    merged = merge_technical_evidence(manual_result)

    assert merged == manual_result


def test_merge_with_no_extractions_returns_empty_result() -> None:
    merged = merge_technical_evidence()

    assert merged.evidence == []
    assert merged.skills == []
