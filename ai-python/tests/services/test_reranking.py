import pytest

from app.schemas.embedding import EmbeddingVector
from app.services.reranking import rerank_candidates


def make_vector(values: list[float]) -> EmbeddingVector:
    return EmbeddingVector(values=values, model="test-model", dimension=len(values), version="v1")


def test_rerank_candidates_orders_by_similarity_descending() -> None:
    query = make_vector([1.0, 0.0])
    candidates = {
        "low": make_vector([0.3, 0.95]),
        "high": make_vector([0.99, 0.14]),
        "mid": make_vector([0.7, 0.71]),
    }

    result = rerank_candidates(query, candidates, minimum_similarity=0.0)

    assert [c.candidate_id for c in result.ranked] == ["high", "mid", "low"]
    assert result.excluded_candidate_ids == []


def test_rerank_candidates_excludes_below_minimum_similarity() -> None:
    query = make_vector([1.0, 0.0])
    candidates = {
        "matches": make_vector([1.0, 0.0]),
        "orthogonal": make_vector([0.0, 1.0]),
    }

    result = rerank_candidates(query, candidates, minimum_similarity=0.5)

    assert [c.candidate_id for c in result.ranked] == ["matches"]
    assert result.excluded_candidate_ids == ["orthogonal"]


def test_rerank_candidates_returns_empty_ranking_when_none_meet_threshold() -> None:
    query = make_vector([1.0, 0.0])
    candidates = {"orthogonal": make_vector([0.0, 1.0])}

    result = rerank_candidates(query, candidates, minimum_similarity=0.9)

    assert result.ranked == []
    assert result.excluded_candidate_ids == ["orthogonal"]
    assert result.minimum_similarity == 0.9


def test_rerank_candidates_handles_empty_candidates() -> None:
    query = make_vector([1.0, 0.0])

    result = rerank_candidates(query, {}, minimum_similarity=0.5)

    assert result.ranked == []
    assert result.excluded_candidate_ids == []


def test_rerank_candidates_breaks_ties_by_candidate_id_ascending() -> None:
    query = make_vector([1.0, 0.0])
    candidates = {
        "b_candidate": make_vector([1.0, 0.0]),
        "a_candidate": make_vector([1.0, 0.0]),
    }

    result = rerank_candidates(query, candidates, minimum_similarity=0.0)

    assert [c.candidate_id for c in result.ranked] == ["a_candidate", "b_candidate"]


@pytest.mark.parametrize("minimum_similarity", [-1.5, 1.5])
def test_rerank_candidates_rejects_minimum_similarity_out_of_range(
    minimum_similarity: float,
) -> None:
    query = make_vector([1.0, 0.0])

    with pytest.raises(ValueError, match="minimum_similarity"):
        rerank_candidates(query, {}, minimum_similarity=minimum_similarity)
