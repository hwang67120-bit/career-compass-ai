"""임베딩 유사도를 바탕으로 후보를 재정렬한다."""

import logging

from app.schemas.embedding import EmbeddingVector
from app.schemas.reranking import RankedCandidate, RerankResult
from app.services.similarity import calculate_cosine_similarity

logger = logging.getLogger(__name__)


def rerank_candidates(
    query: EmbeddingVector,
    candidates: dict[str, EmbeddingVector],
    minimum_similarity: float,
) -> RerankResult:
    """쿼리 임베딩과 후보 임베딩들의 유사도를 계산해 순위를 매긴다.

    입력:
        query: 기준이 되는 임베딩(예: 사용자 프로필).
        candidates: 후보 식별자와 임베딩의 매핑(예: 채용공고 후보).
        minimum_similarity: 이 값보다 낮은 후보는 순위에서 제외한다. -1.0에서
            1.0 사이의 값이어야 한다.

    반환:
        기준을 충족한 후보의 순위. 유사도 내림차순으로 정렬하고, 유사도가
        같으면 candidate_id 오름차순으로 정렬해 순서를 결정한다(동점 처리).
        제외된 후보 식별자 목록과 사용한 기준값도 함께 반환한다. 기준을
        충족한 후보가 없으면 빈 순위를 반환한다.

    예외:
        ValueError: minimum_similarity가 -1.0에서 1.0 사이가 아닌 경우.
        EmbeddingMismatchError: query와 후보 임베딩을 비교할 수 없는 경우
            (`calculate_cosine_similarity`에서 발생).
    """
    if not -1.0 <= minimum_similarity <= 1.0:
        raise ValueError("minimum_similarity는 -1.0에서 1.0 사이의 값이어야 합니다.")

    ranked: list[RankedCandidate] = []
    excluded_candidate_ids: list[str] = []

    for candidate_id, vector in candidates.items():
        similarity = calculate_cosine_similarity(query, vector)
        if similarity < minimum_similarity:
            logger.debug(
                "후보 %s 유사도 기준 미달로 제외: %.4f < %.4f",
                candidate_id,
                similarity,
                minimum_similarity,
            )
            excluded_candidate_ids.append(candidate_id)
            continue
        ranked.append(RankedCandidate(candidate_id=candidate_id, similarity=similarity))

    ranked.sort(key=lambda candidate: (-candidate.similarity, candidate.candidate_id))

    return RerankResult(
        ranked=ranked,
        excluded_candidate_ids=excluded_candidate_ids,
        minimum_similarity=minimum_similarity,
    )
