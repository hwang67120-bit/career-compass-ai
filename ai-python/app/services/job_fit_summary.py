"""채용공고의 필수·우대 기술과 사용자의 검증된 기술을 비교한다.

부족한 기술과 추천 이유를 만드는 방식은 결정론적 구조화 데이터다(2026-08-01
사용자 확인) — LLM으로 자연어 추천 문장을 짓지 않는다. 일치·불일치 여부와
유사도 점수만 반환하고, 이걸 문장으로 꾸미는 건 프론트엔드·Java의 몫이다.
근거 없는 값을 만들지 않는다는 원칙(`AGENTS.md`)을 자연스럽게 지킨다 —
모든 항목이 "채용공고에 실제로 적힌 기술" 대 "사용자가 실제로 보유한
기술"의 비교 결과이기 때문이다.

기술명 일치는 대소문자만 무시하고 정확히 같은 문자열만 같은 기술로 본다
(확인 필요 — `app/services/technical_profile.py`와 같은 한계. 동의어·오타
정규화는 `app/services/skill_tag_matching.py`가 Java 쪽과 연결된 뒤에나
가능하다).
"""

from app.schemas.job_fit_summary import JobFitSummary, SkillFit
from app.schemas.job_posting import JobPostingExtraction


def summarize_job_fit(
    job_posting: JobPostingExtraction,
    user_skill_names: list[str],
    similarity: float | None = None,
) -> JobFitSummary:
    """채용공고의 필수·우대 기술 각각이 사용자 기술 목록에 있는지 확인한다(순수 함수).

    입력:
        job_posting: 채용공고 구조화 결과.
        user_skill_names: 검증된 기술명 목록(저장소 근거·수기 입력 병합 결과,
            `app/services/technical_profile.py`의 출력).
        similarity: 이 채용공고에 대한 사용자 경험 의미 유사도(있으면).

    반환:
        필수·우대 기술 각각의 일치 여부와 유사도를 담은 요약.
    """
    normalized_user_skills = {name.strip().lower() for name in user_skill_names if name.strip()}

    skills: list[SkillFit] = []
    for skill in job_posting.required_skills:
        skills.append(
            SkillFit(
                skill_name=skill.raw_name,
                required=True,
                matched=skill.raw_name.strip().lower() in normalized_user_skills,
            )
        )
    for skill in job_posting.preferred_skills:
        skills.append(
            SkillFit(
                skill_name=skill.raw_name,
                required=False,
                matched=skill.raw_name.strip().lower() in normalized_user_skills,
            )
        )

    return JobFitSummary(skills=skills, similarity=similarity)


def missing_skill_names(summary: JobFitSummary, *, required_only: bool = False) -> list[str]:
    """요약에서 부족한(미보유) 기술 이름만 뽑는다(순수 함수, 편의 함수).

    입력:
        summary: `summarize_job_fit`의 결과.
        required_only: `True`면 필수 기술 중 부족한 것만 반환한다.

    반환:
        부족한 기술 이름 목록(공고에 적힌 순서).
    """
    return [
        skill.skill_name
        for skill in summary.skills
        if not skill.matched and (skill.required or not required_only)
    ]
