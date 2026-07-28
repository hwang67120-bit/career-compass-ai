"""Ollama·Gemini의 임베딩 API를 호출한다."""

import httpx
from google import genai
from google.genai import errors

from app.schemas.embedding import EmbeddingVector

EMBEDDING_PIPELINE_VERSION = "v1"


class EmbeddingUnavailableError(RuntimeError):
    """임베딩 제공자에 연결할 수 없거나 서버 오류가 발생한 경우다."""


class EmbeddingResponseError(RuntimeError):
    """임베딩 응답이 예상 형식과 다르거나 요청이 거부된 경우다."""


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


class GeminiEmbeddingProvider:
    """Gemini의 임베딩 모델을 호출한다."""

    def __init__(self, client: genai.Client, model_name: str) -> None:
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
            EmbeddingUnavailableError: 연결 실패 또는 Gemini 서버 오류.
            EmbeddingResponseError: 요청 거부 또는 응답이 예상 형식과 다른 경우.
        """
        if not texts or any(not text.strip() for text in texts):
            raise ValueError("texts에 빈 값이 있으면 임베딩을 생성할 수 없습니다.")

        try:
            # list[str]은 SDK가 실제로 허용하는 값이지만, mypy가 list의
            # 무공변성 때문에 Union 리스트 타입과 다르다고 오탐지한다.
            response = await self.client.aio.models.embed_content(
                model=self.model_name,
                contents=texts,  # type: ignore[arg-type]
            )
        except errors.ServerError as error:
            raise EmbeddingUnavailableError(
                "Gemini 임베딩 서버 오류가 발생했습니다."
            ) from error
        except errors.ClientError as error:
            raise EmbeddingResponseError("Gemini 임베딩 요청이 거부되었습니다.") from error
        except httpx.HTTPError as error:
            raise EmbeddingUnavailableError("Gemini 임베딩 요청에 실패했습니다.") from error

        embeddings = response.embeddings
        if embeddings is None or len(embeddings) != len(texts):
            raise EmbeddingResponseError(
                "Gemini가 반환한 임베딩 개수가 입력 개수와 다릅니다."
            )

        return [
            EmbeddingVector(
                values=list(embedding.values or []),
                model=self.model_name,
                dimension=len(embedding.values or []),
                version=EMBEDDING_PIPELINE_VERSION,
            )
            for embedding in embeddings
        ]
