"""nomic-embed-text가 "채용공고 근거 vs 사용자 프로젝트 근거" 형태에서도
도메인을 구분하는지 빠르게 재검증한다(스파이크, 2026-08-11).

계층: `docs/architecture/layer-terminology.md`의 오프라인 모델 개발 영역이다.
운영 FastAPI 앱(`app/`)은 이 모듈을 import하지 않는다.

`docs/architecture/embedding-similarity.md`의 2026-07-28 실험은 이력서·채용공고
전체 문단으로 nomic-embed-text가 도메인을 못 구분한다는 걸 보였다(백엔드-프론트엔드
0.9720 > 정답 쌍 0.8720). PR #63(`contracts/job-evidence-similarity.md`)이 제안한
계약은 문단이 아니라 짧은 근거 문장(RESPONSIBILITY/TECHNOLOGY 단위)을 비교하므로,
같은 결론이 이 더 짧은 단위에도 적용되는지 별도로 확인한다.

실행:
    cd ai-python
    .venv/Scripts/python.exe -m evaluation.job_evidence_similarity_spike
"""

import asyncio
import sys

import httpx

from app.providers.embedding import OllamaEmbeddingProvider
from app.providers.settings import OllamaSettings

# contracts/job-evidence-similarity.md의 예시와 같은 형태(RESPONSIBILITY,
# REQUIRED_SKILL/PREFERRED_SKILL vs PROJECT_RESPONSIBILITY/PROJECT_TECHNOLOGY).
JOB_EVIDENCE = {
    "job-responsibility-backend": (
        "RESPONSIBILITY", "대규모 트래픽을 처리하는 백엔드 API를 설계하고 운영합니다."
    ),
    "job-skill-spring": ("REQUIRED_SKILL", "Spring Boot"),
    "job-skill-python": ("REQUIRED_SKILL", "Python"),
}

USER_EVIDENCE = {
    "user-responsibility-backend": (
        "PROJECT_RESPONSIBILITY",
        "Redis 캐시와 비동기 작업을 적용해 API 응답 부하를 줄였습니다.",
    ),
    "user-responsibility-frontend": (
        "PROJECT_RESPONSIBILITY",
        "React와 TypeScript로 반응형 대시보드 UI를 구현했습니다.",
    ),
    "user-tech-spring": ("PROJECT_TECHNOLOGY", "Spring Boot"),
    "user-tech-react": ("PROJECT_TECHNOLOGY", "React"),
    "user-tech-java": ("PROJECT_TECHNOLOGY", "Java"),
}

# (job_id, user_id) -> 이 조합이 실제로 같은 기술/영역이면 True(높은 유사도 기대),
# 다른 영역이면 False(낮은 유사도 기대). 계약의 dimension 규칙(RESPONSIBILITY<->
# PROJECT_RESPONSIBILITY, *_SKILL<->PROJECT_TECHNOLOGY)을 따른다.
EXPECTED_MATCH = {
    ("job-responsibility-backend", "user-responsibility-backend"): True,
    ("job-responsibility-backend", "user-responsibility-frontend"): False,
    ("job-skill-spring", "user-tech-spring"): True,
    ("job-skill-spring", "user-tech-react"): False,
    ("job-skill-python", "user-tech-java"): False,
    ("job-skill-python", "user-tech-spring"): False,
}


def cosine_similarity(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b, strict=True))
    norm_a = sum(x * x for x in a) ** 0.5
    norm_b = sum(y * y for y in b) ** 0.5
    return dot / (norm_a * norm_b)


async def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", line_buffering=True)
    settings = OllamaSettings()
    model_name = settings.ollama_embedding_model
    print(f"[nomic] 모델: {model_name}")

    async with httpx.AsyncClient(base_url=str(settings.ollama_base_url).rstrip("/")) as client:
        provider = OllamaEmbeddingProvider(client=client, model_name=model_name)

        job_ids = list(JOB_EVIDENCE)
        user_ids = list(USER_EVIDENCE)
        job_vectors = await provider.embed([JOB_EVIDENCE[i][1] for i in job_ids])
        user_vectors = await provider.embed([USER_EVIDENCE[i][1] for i in user_ids])

    job_vec_by_id = dict(zip(job_ids, job_vectors, strict=True))
    user_vec_by_id = dict(zip(user_ids, user_vectors, strict=True))

    print("\n=== 유사도 표 (행=채용공고 근거, 열=사용자 근거) ===")
    header = "".ljust(30) + "".join(uid.ljust(28) for uid in user_ids)
    print(header)
    for jid in job_ids:
        row = jid.ljust(30)
        for uid in user_ids:
            score = cosine_similarity(job_vec_by_id[jid].values, user_vec_by_id[uid].values)
            row += f"{score:.4f}".ljust(28)
        print(row)

    print("\n=== 판정: 정답 쌍이 오답 쌍보다 높은가? ===")
    all_correct = True
    for jid in job_ids:
        matches = [uid for uid in user_ids if EXPECTED_MATCH.get((jid, uid)) is True]
        mismatches = [uid for uid in user_ids if EXPECTED_MATCH.get((jid, uid)) is False]
        if not matches or not mismatches:
            continue
        match_scores = [cosine_similarity(job_vec_by_id[jid].values, user_vec_by_id[uid].values) for uid in matches]
        mismatch_scores = [
            cosine_similarity(job_vec_by_id[jid].values, user_vec_by_id[uid].values) for uid in mismatches
        ]
        min_match = min(match_scores)
        max_mismatch = max(mismatch_scores)
        ok = min_match > max_mismatch
        all_correct = all_correct and ok
        status = "OK" if ok else "실패(역전)"
        print(
            f"  {jid}: 정답 최소 {min_match:.4f} vs 오답 최대 {max_mismatch:.4f} -> {status}"
        )

    print(f"\n결론: {'모든 조합에서 정답 쌍이 더 높았다' if all_correct else '하나 이상의 조합에서 역전이 발생했다'}")


if __name__ == "__main__":
    asyncio.run(main())
