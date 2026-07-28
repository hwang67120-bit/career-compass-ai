import pytest

from app.schemas.embedding import EmbeddingVector
from app.services.similarity import EmbeddingMismatchError, calculate_cosine_similarity


def make_vector(values: list[float], model: str = "test-model", version: str = "v1") -> EmbeddingVector:
    return EmbeddingVector(values=values, model=model, dimension=len(values), version=version)


def test_calculate_cosine_similarity_returns_one_for_identical_vectors() -> None:
    left = make_vector([1.0, 0.0])
    right = make_vector([1.0, 0.0])

    assert calculate_cosine_similarity(left, right) == pytest.approx(1.0)


def test_calculate_cosine_similarity_returns_zero_for_orthogonal_vectors() -> None:
    left = make_vector([1.0, 0.0])
    right = make_vector([0.0, 1.0])

    assert calculate_cosine_similarity(left, right) == pytest.approx(0.0)


def test_calculate_cosine_similarity_rejects_different_models() -> None:
    left = make_vector([1.0, 0.0], model="model-a")
    right = make_vector([1.0, 0.0], model="model-b")

    with pytest.raises(EmbeddingMismatchError):
        calculate_cosine_similarity(left, right)


def test_calculate_cosine_similarity_rejects_different_dimensions() -> None:
    left = make_vector([1.0, 0.0])
    right = make_vector([1.0, 0.0, 0.0])

    with pytest.raises(EmbeddingMismatchError):
        calculate_cosine_similarity(left, right)


def test_calculate_cosine_similarity_rejects_zero_vector() -> None:
    left = make_vector([0.0, 0.0])
    right = make_vector([1.0, 0.0])

    with pytest.raises(EmbeddingMismatchError):
        calculate_cosine_similarity(left, right)
