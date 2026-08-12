"""공개 GitHub 저장소의 파일 목록과 내용을 조회한다.

Java가 `/api/v1/project-sources/github`에서 이미 저장소 존재·기본 브랜치·
`commitSha`를 확인한 뒤이므로, 여기서는 그 결과(owner/repo/commitSha)를
가지고 코드 내용만 조회한다. Java의 `GitHubRestClient`와 마찬가지로 인증
토큰 없이 공개 API만 호출한다.
"""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from urllib.parse import quote

import httpx

from app.providers.settings import GitHubRepositorySettings

_logger = logging.getLogger(__name__)


class GitHubRepositoryUnavailableError(RuntimeError):
    """GitHub에 연결할 수 없거나 예상하지 못한 오류 응답을 받은 경우다."""


class GitHubRepositoryNotFoundError(RuntimeError):
    """저장소·커밋·파일을 찾을 수 없는 경우다(등록 이후 삭제·재작성됐을 수 있음)."""


class GitHubRepositoryRateLimitedError(RuntimeError):
    """GitHub API 요청 한도에 도달한 경우다."""


class GitHubRepositoryClient:
    """공개 저장소의 파일 트리와 파일 내용을 조회한다.

    각 메서드는 기본적으로 호출마다 새 `httpx.AsyncClient`를 만들어 쓰고
    닫는다 — `app/providers/ollama_client.py`의 주석대로, 클라이언트를
    인스턴스에 오래 들고 있으면 이벤트 루프가 바뀔 때(테스트 등) "Event loop
    is closed" 오류가 실제로 재현된 적이 있어서다.

    다만 저장소 하나를 분석하는 동안(트리 조회 + 매니페스트 파일 여러 개
    조회)은 같은 이벤트 루프 안에서 끝나므로, `open_session()`으로 만든
    클라이언트를 `http_client` 인자로 넘기면 연결을 재사용해 TCP·TLS
    핸드셰이크를 반복하지 않는다. `repository_readme.fetch_repository_readmes()`가
    이 방식을 쓴다.
    """

    def __init__(self, settings: GitHubRepositorySettings | None = None) -> None:
        self._settings = settings or GitHubRepositorySettings()

    def open_session(self) -> httpx.AsyncClient:
        """하나의 분석 실행 동안 여러 조회에 재사용할 클라이언트를 만든다.

        호출자가 `async with`로 관리하고, 그 범위 안에서만 여러 메서드에
        `http_client`로 넘겨써야 한다. 여러 요청·이벤트 루프에 걸쳐
        재사용하지 않는다.
        """
        return self._build_client()

    async def fetch_tree(
        self,
        owner: str,
        repository: str,
        commit_sha: str,
        http_client: httpx.AsyncClient | None = None,
    ) -> list[str]:
        """지정한 커밋 시점의 전체 파일 경로 목록을 재귀적으로 조회한다.

        입력:
            owner: 저장소 소유자.
            repository: 저장소 이름.
            commit_sha: 조회 시점의 커밋(Java가 등록 시 확인한 값).
            http_client: `open_session()`으로 만든 클라이언트를 재사용하려면
                전달한다. 생략하면 이 호출에서만 쓰고 닫는 클라이언트를 만든다.

        반환:
            파일(blob) 경로 목록. 디렉터리(tree) 항목은 제외한다.
            GitHub가 응답을 자른 경우(`truncated`) 경고 로그를 남긴다 — 이
            경우 반환된 목록이 저장소 전체를 담고 있지 않을 수 있다.

        예외:
            GitHubRepositoryNotFoundError: 저장소나 커밋을 찾을 수 없음.
            GitHubRepositoryRateLimitedError: GitHub API 요청 한도 도달.
            GitHubRepositoryUnavailableError: 그 외 연결·응답 오류.
        """
        url = (
            f"{str(self._settings.github_api_base_url).rstrip('/')}"
            f"/repos/{owner}/{repository}/git/trees/{commit_sha}"
        )
        try:
            async with self._client_scope(http_client) as client:
                response = await client.get(url, params={"recursive": "1"})
        except httpx.HTTPError as error:
            raise GitHubRepositoryUnavailableError(
                "GitHub 저장소 트리를 조회하지 못했습니다."
            ) from error

        if response.status_code == 404:
            raise GitHubRepositoryNotFoundError(
                "저장소 또는 커밋을 찾을 수 없습니다."
            )
        if response.status_code in (403, 429):
            raise GitHubRepositoryRateLimitedError(
                "GitHub API 요청 한도에 도달했습니다."
            )
        if response.status_code != 200:
            raise GitHubRepositoryUnavailableError(
                f"GitHub 저장소 트리 조회가 실패했습니다(status={response.status_code})."
            )

        try:
            payload = response.json()
        except ValueError as error:
            raise GitHubRepositoryUnavailableError(
                "GitHub 저장소 트리 응답을 해석할 수 없습니다."
            ) from error

        if payload.get("truncated"):
            _logger.warning(
                "GitHub 저장소 트리가 잘렸습니다(truncated) — 일부 파일이 "
                "근거 추출에서 빠질 수 있습니다: %s/%s@%s",
                owner,
                repository,
                commit_sha,
            )

        return [
            entry["path"]
            for entry in payload.get("tree", [])
            if entry.get("type") == "blob" and entry.get("path")
        ]

    async def fetch_file_text(
        self,
        owner: str,
        repository: str,
        commit_sha: str,
        file_path: str,
        http_client: httpx.AsyncClient | None = None,
    ) -> str:
        """지정한 커밋 시점의 파일 원문을 조회한다.

        raw.githubusercontent.com을 사용한다 — api.github.com의 시간당 60회
        제한과 별도로 계산되고, base64 디코딩 없이 원문을 바로 받을 수 있다.

        예외:
            GitHubRepositoryNotFoundError: 파일을 찾을 수 없음.
            GitHubRepositoryUnavailableError: 그 외 연결·응답 오류.
        """
        encoded_path = quote(file_path, safe="/")
        url = (
            f"{str(self._settings.github_raw_base_url).rstrip('/')}"
            f"/{owner}/{repository}/{commit_sha}/{encoded_path}"
        )
        try:
            async with self._client_scope(http_client) as client:
                response = await client.get(url)
        except httpx.HTTPError as error:
            raise GitHubRepositoryUnavailableError(
                f"GitHub 파일을 조회하지 못했습니다: {file_path}"
            ) from error

        if response.status_code == 404:
            raise GitHubRepositoryNotFoundError(f"파일을 찾을 수 없습니다: {file_path}")
        if response.status_code != 200:
            raise GitHubRepositoryUnavailableError(
                f"GitHub 파일 조회가 실패했습니다(status={response.status_code}): {file_path}"
            )

        return response.text

    def _build_client(self) -> httpx.AsyncClient:
        timeout = httpx.Timeout(
            connect=self._settings.github_api_connect_timeout_seconds,
            read=self._settings.github_api_read_timeout_seconds,
            write=10.0,
            pool=5.0,
        )
        return httpx.AsyncClient(
            timeout=timeout,
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=50),
        )

    @asynccontextmanager
    async def _client_scope(
        self, http_client: httpx.AsyncClient | None
    ) -> AsyncIterator[httpx.AsyncClient]:
        """전달받은 클라이언트가 있으면 그대로 쓰고, 없으면 만들어 쓰고 닫는다."""
        if http_client is not None:
            yield http_client
            return
        async with self._build_client() as owned_client:
            yield owned_client
