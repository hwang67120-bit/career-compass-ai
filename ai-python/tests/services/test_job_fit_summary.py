from app.schemas.job_posting import JobPostingExtraction, JobPostingSkill
from app.services.job_fit_summary import missing_skill_names, summarize_job_fit


def make_skill(raw_name: str) -> JobPostingSkill:
    return JobPostingSkill(raw_name=raw_name, evidence_ids=["e1"])


def test_summarize_job_fit_marks_matched_and_missing_required_skills() -> None:
    job_posting = JobPostingExtraction(
        required_skills=[make_skill("Java"), make_skill("Kubernetes")],
    )

    summary = summarize_job_fit(job_posting, user_skill_names=["Java", "Spring Boot"])

    fit_by_name = {skill.skill_name: skill for skill in summary.skills}
    assert fit_by_name["Java"].matched is True
    assert fit_by_name["Java"].required is True
    assert fit_by_name["Kubernetes"].matched is False


def test_summarize_job_fit_separates_required_and_preferred() -> None:
    job_posting = JobPostingExtraction(
        required_skills=[make_skill("Java")],
        preferred_skills=[make_skill("AWS")],
    )

    summary = summarize_job_fit(job_posting, user_skill_names=["Java"])

    required = [skill for skill in summary.skills if skill.required]
    preferred = [skill for skill in summary.skills if not skill.required]
    assert [skill.skill_name for skill in required] == ["Java"]
    assert [skill.skill_name for skill in preferred] == ["AWS"]
    assert preferred[0].matched is False


def test_summarize_job_fit_matches_case_insensitively() -> None:
    job_posting = JobPostingExtraction(required_skills=[make_skill("java")])

    summary = summarize_job_fit(job_posting, user_skill_names=["Java"])

    assert summary.skills[0].matched is True


def test_summarize_job_fit_carries_similarity_through() -> None:
    job_posting = JobPostingExtraction(required_skills=[make_skill("Java")])

    summary = summarize_job_fit(job_posting, user_skill_names=["Java"], similarity=0.821)

    assert summary.similarity == 0.821


def test_summarize_job_fit_handles_no_skills() -> None:
    summary = summarize_job_fit(JobPostingExtraction(), user_skill_names=["Java"])

    assert summary.skills == []


def test_missing_skill_names_returns_unmatched_skills_in_order() -> None:
    job_posting = JobPostingExtraction(
        required_skills=[make_skill("Java"), make_skill("Kubernetes")],
        preferred_skills=[make_skill("AWS")],
    )
    summary = summarize_job_fit(job_posting, user_skill_names=["Java"])

    assert missing_skill_names(summary) == ["Kubernetes", "AWS"]


def test_missing_skill_names_can_filter_to_required_only() -> None:
    job_posting = JobPostingExtraction(
        required_skills=[make_skill("Java"), make_skill("Kubernetes")],
        preferred_skills=[make_skill("AWS")],
    )
    summary = summarize_job_fit(job_posting, user_skill_names=["Java"])

    assert missing_skill_names(summary, required_only=True) == ["Kubernetes"]
