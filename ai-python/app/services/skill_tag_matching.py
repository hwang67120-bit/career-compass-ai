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
"""

from typing import Protocol

from app.schemas.embedding import EmbeddingVector
from app.schemas.skill_tag_match import SkillTagMatch, TagMatchRecommendation
from app.services.similarity import calculate_cosine_similarity

# 확인 필요 — Gemini 임베딩으로 예시 7쌍만 확인한 값이라 표본이 작다.
# 오타·번역 표기 쌍(스프링부트/Spring Boot 0.76, Spring boot 3/Spring Boot 0.77,
# JS/JavaScript 0.76, 리액트/React 0.83)과 실제로 다른 기술 쌍
# (Kubernetes/Spring Boot 0.64, Python/Java 0.66, 우쿨렐레/Spring Boot 0.56)
# 사이 간격에 놓았다. 다른 임베딩 모델·더 많은 예시로 재평가가 필요하다.
SUGGESTION_SIMILARITY_THRESHOLD = 0.72


class EmbeddingProvider(Protocol):
    """`OllamaEmbeddingProvider`·`GeminiEmbeddingProvider`가 공통으로 구현하는 부분이다."""

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]: ...


def find_best_canonical_match(
    candidate_vector: EmbeddingVector,
    canonical_tags: list[str],
    canonical_vectors: list[EmbeddingVector],
) -> tuple[str, float] | None:
    """후보 임베딩과 가장 유사한 고정 태그를 찾는다(순수 함수).

    입력:
        candidate_vector: 후보 태그의 임베딩.
        canonical_tags: 고정 태그 목록(임베딩과 같은 순서).
        canonical_vectors: 고정 태그들의 임베딩.

    반환:
        가장 유사한 (고정 태그, 코사인 유사도). 목록이 비어 있으면 `None`.
    """
    best: tuple[str, float] | None = None
    for tag, vector in zip(canonical_tags, canonical_vectors, strict=True):
        similarity = calculate_cosine_similarity(candidate_vector, vector)
        if best is None or similarity > best[1]:
            best = (tag, similarity)
    return best


def decide_skill_tag_match(
    candidate_tag: str,
    canonical_tags: list[str],
    best_match: tuple[str, float] | None,
) -> SkillTagMatch:
    """정확 일치와 유사도 임계값으로 최종 권고를 만든다(순수 함수, 임베딩 계산 없음).

    입력:
        candidate_tag: 판단할 후보 태그(사용자 입력 등).
        canonical_tags: 고정 태그 목록 — 정확 일치(대소문자 무시)만 여기서 다시 확인한다.
        best_match: `find_best_canonical_match`의 결과. 후보가 이미 정확히
            일치하면 무시된다.

    반환:
        `EXACT_MATCH`, `SUGGEST_CORRECTION`, `NO_MATCH` 중 하나로 판단한 결과.
    """
    normalized_candidate = candidate_tag.strip().lower()
    for tag in canonical_tags:
        if tag.strip().lower() == normalized_candidate:
            return SkillTagMatch(
                candidate_tag=candidate_tag,
                recommendation=TagMatchRecommendation.EXACT_MATCH,
                best_match_tag=tag,
                similarity=1.0,
            )

    if best_match is None:
        return SkillTagMatch(
            candidate_tag=candidate_tag,
            recommendation=TagMatchRecommendation.NO_MATCH,
            best_match_tag=None,
            similarity=None,
        )

    best_tag, best_similarity = best_match
    recommendation = (
        TagMatchRecommendation.SUGGEST_CORRECTION
        if best_similarity >= SUGGESTION_SIMILARITY_THRESHOLD
        else TagMatchRecommendation.NO_MATCH
    )
    return SkillTagMatch(
        candidate_tag=candidate_tag,
        recommendation=recommendation,
        best_match_tag=best_tag,
        similarity=best_similarity,
    )


async def match_skill_tag(
    provider: EmbeddingProvider, candidate_tag: str, canonical_tags: list[str]
) -> SkillTagMatch:
    """후보 태그 하나를 고정 태그 목록과 비교하는 전체 과정을 수행한다.

    정확히 일치하거나 고정 태그 목록이 비어 있으면 임베딩 호출 없이 바로
    반환한다.

    입력:
        provider: 임베딩을 만들 provider(`OllamaEmbeddingProvider` 등).
        candidate_tag: 판단할 후보 태그.
        canonical_tags: 고정 태그 목록.

    반환:
        `decide_skill_tag_match`와 동일한 결과.

    예외:
        provider가 던지는 예외(예: `EmbeddingUnavailableError`)를 그대로 전달한다.
    """
    exact_check = decide_skill_tag_match(candidate_tag, canonical_tags, best_match=None)
    if exact_check.recommendation == TagMatchRecommendation.EXACT_MATCH or not canonical_tags:
        return exact_check

    vectors = await provider.embed([candidate_tag, *canonical_tags])
    candidate_vector, *canonical_vectors = vectors
    best_match = find_best_canonical_match(candidate_vector, canonical_tags, canonical_vectors)
    return decide_skill_tag_match(candidate_tag, canonical_tags, best_match)
