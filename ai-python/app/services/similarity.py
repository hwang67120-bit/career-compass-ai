"""임베딩 벡터 사이의 의미 유사도를 계산한다."""

import numpy as np

from app.schemas.embedding import EmbeddingVector


class EmbeddingMismatchError(ValueError):
    """서로 비교할 수 없는 임베딩 쌍인 경우다."""


def calculate_cosine_similarity(left: EmbeddingVector, right: EmbeddingVector) -> float:
    """두 임베딩 벡터의 코사인 유사도를 계산한다.

    입력:
        left: 비교할 첫 번째 임베딩.
        right: 비교할 두 번째 임베딩.

    반환:
        -1.0에서 1.0 사이의 코사인 유사도. 부동소수점 오차로 범위를 벗어난
        값은 -1.0/1.0으로 고정한다.

    예외:
        EmbeddingMismatchError: 두 임베딩의 모델·버전·차원이 다르거나
            벡터가 비어 있거나 영벡터인 경우.
    """
    if left.model != right.model or left.version != right.version:
        raise EmbeddingMismatchError(
            "서로 다른 모델·버전으로 생성된 임베딩은 비교할 수 없습니다."
        )
    if left.dimension != right.dimension:
        raise EmbeddingMismatchError("두 임베딩의 벡터 차원이 다릅니다.")
    if left.dimension == 0:
        raise EmbeddingMismatchError("빈 임베딩은 비교할 수 없습니다.")

    left_vector = np.array(left.values, dtype=np.float32)
    right_vector = np.array(right.values, dtype=np.float32)

    left_norm = np.linalg.norm(left_vector)
    right_norm = np.linalg.norm(right_vector)

    if left_norm == 0.0 or right_norm == 0.0:
        raise EmbeddingMismatchError("영벡터는 코사인 유사도를 계산할 수 없습니다.")

    similarity = np.dot(left_vector, right_vector) / (left_norm * right_norm)

    return float(np.clip(similarity, -1.0, 1.0))
