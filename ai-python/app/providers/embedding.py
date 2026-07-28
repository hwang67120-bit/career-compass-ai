"""로컬 Ollama의 임베딩 API를 호출한다."""

import httpx

from app.schemas.embedding import EmbeddingVector

EMBEDDING_PIPELINE_VERSION = "v1"


class EmbeddingUnavailableError(RuntimeError):
    """Ollama에 연결할 수 없거나 임베딩 모델이 준비되지 않은 경우다."""


class EmbeddingResponseError(RuntimeError):
    """Ollama 임베딩 응답이 예상 형식과 다른 경우다."""


class OllamaEmbeddingProvider:
    """로컬 Ollama의 임베딩 모델을 호출한다."""

    def __init__(self, client: httpx.AsyncClient, model_name: str) -> None:
        self.client = client
        self.model_name = model_name

    async def embed(self, texts: list[str]) -> list[EmbeddingVector]:
        """텍스트 목록을 임베딩 벡터로 변환한다.

        입력:
            texts: 벡터로 변환할 텍스트 목록. 빈 목록이나 빈 문자열은 허용하지 않는다.

        반환:
            입력 순서와 동일한 순서의 임베딩 벡터 목록. 각 벡터에는 모델 이름,
            차원과 생성 버전이 함께 포함된다.

        예외:
            ValueError: texts가 비어 있거나 빈 문자열을 포함한 경우.
            EmbeddingUnavailableError: 연결 실패, 제한시간 초과 또는 요청 실패.
            EmbeddingResponseError: 응답이 예상 형식과 다른 경우.
        """
        if not texts or any(not text.strip() for text in texts):
            raise ValueError("texts에 빈 값이 있으면 임베딩을 생성할 수 없습니다.")

        try:
            response = await self.client.post(
                "/api/embed",
                json={"model": self.model_name, "input": texts},
            )
            response.raise_for_status()
            body = response.json()
        except httpx.TimeoutException as error:
            raise EmbeddingUnavailableError(
                "Ollama 임베딩 응답 제한시간을 초과했습니다."
            ) from error
        except httpx.HTTPError as error:
            raise EmbeddingUnavailableError(
                "Ollama 임베딩 요청에 실패했습니다."
            ) from error

        try:
            embeddings = body["embeddings"]
        except (KeyError, TypeError) as error:
            raise EmbeddingResponseError(
                "Ollama 임베딩 응답이 예상 형식과 다릅니다."
            ) from error

        if len(embeddings) != len(texts):
            raise EmbeddingResponseError(
                "Ollama가 반환한 임베딩 개수가 입력 개수와 다릅니다."
            )

        return [
            EmbeddingVector(
                values=vector,
                model=self.model_name,
                dimension=len(vector),
                version=EMBEDDING_PIPELINE_VERSION,
            )
            for vector in embeddings
        ]
