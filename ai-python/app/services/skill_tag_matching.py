"""후보 기술 태그가 고정 태그 목록의 어떤 태그와 같은지 판단한다.

고정 태그 목록은 실제 채용공고에서 추출된 `rawName`을 모아 만든다
(2026-07-31 사용자 확인, docs/current-work.md 참고) — 사람이 자기소개에
쓰는 추상적인 표현이 아니라 채용 시장에서 실제로 쓰이는 구체적인 이름과
비교해야 정확한 매칭이 된다. 목록 자체의 저장·관리는 Java 책임이고,
Python은 후보 하나를 그 목록과 비교만 한다.

오타·표기 차이는 임베딩 유사도로 감지하지만, Python이 값을 직접 바꾸지
않는다 — `SUGGEST_CORRECTION`을 권고할 뿐이고, 사용자 확인 후 정규화하는
건 Java·사용자 몫이다(`AGENTS.md` "AI 추출 결과는 사용자가 확인하기
전까지 확정 프로필로 사용하지 않는다").

고정 태그가 계속 쌓이면(채용공고를 처리할 때마다 늘어남) 절대 유사도
임계값만으로는 오탐이 늘어난다 — 후보가 많을수록 "그 많은 것 중 우연히
가장 비슷한 것"의 유사도 자체가 통계적으로 올라가는 경향이 있다
(2026-07-31 문제 제기). 그래서 1위와 2위 후보의 유사도 차이(margin)도
같이 확인해서, 1위가 확실히 앞서는 경우에만 제안한다. 재정렬은 이미
검증된 `app/services/reranking.py`의 `rerank_candidates`를 그대로
재사용한다 — 새 정렬·동점 처리 로직을 다시 만들지 않는다.
"""

from typing import Protocol

from app.schemas.embedding import EmbeddingVector
from app.schemas.reranking import RankedCandidate
from app.schemas.skill_tag_match import SkillTagMatch, TagMatchRecommendation
from app.services.reranking import rerank_candidates

# 확인 필요 — Gemini 임베딩으로 예시 7쌍만 확인한 값이라 표본이 작다.
# 오타·번역 표기 쌍(스프링부트/Spring Boot 0.76, Spring boot 3/Spring Boot 0.77,
# JS/JavaScript 0.76, 리액트/React 0.83)과 실제로 다른 기술 쌍
# (Kubernetes/Spring Boot 0.64, Python/Java 0.66, 우쿨렐레/Spring Boot 0.56)
# 사이 간격에 놓았다. 다른 임베딩 모델·더 많은 예시로 재평가가 필요하다.
SUGGESTION_SIMILARITY_THRESHOLD = 0.72

# 확인 필요 — 고정 태그 15개짜리 목록으로 예시 5건만 확인한 값. 진짜 오타
# 쌍(스프링부트→Spring Boot 0.156, Postgre→PostgreSQL 0.148)은 1·2위 차이가
# 크고, 무관하거나 대응 태그가 없는 경우(JS, 우쿨렐레, Node.js)는 0.002~0.021로
# 훨씬 작았다. 그 사이인 0.05로 놓았다 — 고정 태그가 실제로 수백~수천 개로
# 늘어나면 재평가가 필요하다.
MARGIN_THRESHOLD = 0.05


class EmbeddingProvider(Protocol):
    """`OllamaEmbeddingProvider`·`GeminiEmbeddingProvider`가 공통으로 구현하는 부분이다."""

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]: ...


def decide_skill_tag_match(
    candidate_tag: str,
    canonical_tags: list[str],
    ranked: list[RankedCandidate],
) -> SkillTagMatch:
    """정확 일치, 유사도 임계값과 1·2위 차이(margin)로 최종 권고를 만든다(순수 함수).

    입력:
        candidate_tag: 판단할 후보 태그(사용자 입력 등).
        canonical_tags: 고정 태그 목록 — 정확 일치(대소문자 무시)를 먼저 확인한다.
        ranked: `rerank_candidates(minimum_similarity=-1.0)`로 얻은, 필터링 없이
            유사도 내림차순으로 정렬된 전체 순위. margin 계산에 2위가 필요하므로
            걸러내지 않은 전체 순위를 받는다.

    반환:
        - 정확히 일치하면 `EXACT_MATCH`(`margin=None`).
        - 1위 유사도가 `SUGGESTION_SIMILARITY_THRESHOLD` 이상이고, 2위가 있으면
          1·2위 차이가 `MARGIN_THRESHOLD` 이상일 때만 `SUGGEST_CORRECTION`.
          2위가 없으면(고정 태그 1개) margin 없이 임계값만으로 판단한다.
        - 그 외에는 `NO_MATCH`(1위 정보가 있으면 참고용으로 같이 반환).
    """
    normalized_candidate = candidate_tag.strip().lower()
    for tag in canonical_tags:
        if tag.strip().lower() == normalized_candidate:
            return SkillTagMatch(
                candidate_tag=candidate_tag,
                recommendation=TagMatchRecommendation.EXACT_MATCH,
                best_match_tag=tag,
                similarity=1.0,
                margin=None,
            )

    if not ranked:
        return SkillTagMatch(
            candidate_tag=candidate_tag,
            recommendation=TagMatchRecommendation.NO_MATCH,
            best_match_tag=None,
            similarity=None,
            margin=None,
        )

    top = ranked[0]
    margin = top.similarity - ranked[1].similarity if len(ranked) > 1 else None
    meets_threshold = top.similarity >= SUGGESTION_SIMILARITY_THRESHOLD
    meets_margin = margin is None or margin >= MARGIN_THRESHOLD

    recommendation = (
        TagMatchRecommendation.SUGGEST_CORRECTION
        if meets_threshold and meets_margin
        else TagMatchRecommendation.NO_MATCH
    )
    return SkillTagMatch(
        candidate_tag=candidate_tag,
        recommendation=recommendation,
        best_match_tag=top.candidate_id,
        similarity=top.similarity,
        margin=margin,
    )


async def match_skill_tag(
    provider: EmbeddingProvider,
    candidate_tag: str,
    canonical_tags: list[str],
    canonical_vectors: list[EmbeddingVector],
) -> SkillTagMatch:
    """후보 태그 하나를 고정 태그 목록과 비교하는 전체 과정을 수행한다.

    고정 태그들의 임베딩(`canonical_vectors`)은 캐시된 값을 그대로 받는다 —
    고정 태그가 새로 생길 때 한 번만 임베딩해서 저장해두는 건 호출자(Java)
    책임이고, 여기서는 매번 다시 계산하지 않는다. 이 함수는 후보 태그 하나만
    새로 임베딩한다.

    입력:
        provider: 후보 태그를 임베딩할 provider(`OllamaEmbeddingProvider` 등).
        candidate_tag: 판단할 후보 태그.
        canonical_tags: 고정 태그 목록.
        canonical_vectors: `canonical_tags`와 같은 순서·같은 개수의 캐시된 임베딩.

    반환:
        `decide_skill_tag_match`와 동일한 결과.

    예외:
        ValueError: `canonical_tags`와 `canonical_vectors`의 개수가 다른 경우.
        provider가 던지는 예외(예: `EmbeddingUnavailableError`)를 그대로 전달한다.
        EmbeddingMismatchError: 캐시된 임베딩이 후보 임베딩과 다른 모델·버전인 경우
            (`calculate_cosine_similarity`에서 발생) — 캐시가 오래돼 모델이 바뀌었을 때
            조용히 잘못된 값을 내지 않고 여기서 실패한다.
    """
    if len(canonical_tags) != len(canonical_vectors):
        raise ValueError("canonical_tags와 canonical_vectors의 개수가 같아야 합니다.")

    exact_check = decide_skill_tag_match(candidate_tag, canonical_tags, ranked=[])
    if exact_check.recommendation == TagMatchRecommendation.EXACT_MATCH or not canonical_tags:
        return exact_check

    candidate_vector = (await provider.embed([candidate_tag]))[0]
    rerank_result = rerank_candidates(
        query=candidate_vector,
        candidates=dict(zip(canonical_tags, canonical_vectors, strict=True)),
        minimum_similarity=-1.0,
    )
    return decide_skill_tag_match(candidate_tag, canonical_tags, ranked=rerank_result.ranked)
