"""Ollama가 로컬에서 응답하는지 확인하고, 응답하지 않으면 자동으로 실행한다.

Gemini는 API 키만 있으면 호출할 수 있지만 Ollama는 별도 로컬 프로세스
(`ollama serve`)가 떠 있어야 한다. 이 모듈은 그 수동 실행 단계를 없앤다.
Ollama를 자동으로 켜지 못해도 서버 시작 자체는 계속된다 — Ollama 없이도
동작하는 API(health, documents/extract의 PDF 추출 단계 등)는 계속 써야
하기 때문이다.
"""

import shutil
import subprocess
import time
from collections.abc import Callable
from urllib.parse import urlparse

import httpx

from app.providers.settings import OllamaSettings

_POLL_INTERVAL_SECONDS = 0.5
_MAX_WAIT_SECONDS = 10.0
_LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}


def is_local_base_url(base_url: str) -> bool:
    """base_url이 로컬(localhost/127.0.0.1)을 가리키는지. 원격이면 자동 실행하지 않는다."""
    return urlparse(base_url).hostname in _LOCAL_HOSTS


def is_ollama_reachable(base_url: str) -> bool:
    """Ollama API가 지금 응답하는지 확인한다."""
    try:
        response = httpx.get(f"{base_url}/api/tags", timeout=2)
    except httpx.HTTPError:
        return False
    return response.status_code == 200


def find_ollama_executable() -> str | None:
    """실행 가능한 `ollama` 경로를 찾는다. 없으면 `None`이다."""
    return shutil.which("ollama")


def spawn_ollama_serve(ollama_path: str) -> None:
    """`ollama serve`를 백그라운드 프로세스로 실행한다."""
    subprocess.Popen(
        [ollama_path, "serve"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def ensure_ollama_running(
    settings: OllamaSettings | None = None,
    *,
    is_reachable: Callable[[str], bool] = is_ollama_reachable,
    is_local: Callable[[str], bool] = is_local_base_url,
    find_executable: Callable[[], str | None] = find_ollama_executable,
    spawn: Callable[[str], None] = spawn_ollama_serve,
    max_wait_seconds: float = _MAX_WAIT_SECONDS,
    poll_interval_seconds: float = _POLL_INTERVAL_SECONDS,
) -> bool:
    """Ollama가 응답하지 않으면 `ollama serve`를 자동으로 띄운다(로컬 base_url일 때만).

    입력:
        settings: Ollama 연결 설정. 생략하면 환경변수에서 새로 읽는다.
        is_reachable, is_local, find_executable, spawn: 테스트에서 실제 프로세스·
            네트워크 호출 없이 검증할 수 있도록 주입 가능한 지점이다.

    반환:
        True면 Ollama가 (이미 떠 있었거나 이번에 띄워서) 응답 가능하다.
        False면 원격(base_url이 localhost 아님)이라 자동 실행을 안 했거나,
        실행 파일을 찾지 못했거나 제한 시간 안에 응답하지 않았다.
    """
    settings = settings or OllamaSettings()
    base_url = str(settings.ollama_base_url).rstrip("/")

    if is_reachable(base_url):
        return True

    # 원격 Ollama 모드(컨테이너·배포)에서는 로컬 `ollama serve`를 시도하지 않는다.
    # 원격 장애를 로컬 설치·PATH 문제로 오인하지 않기 위함이다.
    if not is_local(base_url):
        return False

    ollama_path = find_executable()
    if ollama_path is None:
        return False

    spawn(ollama_path)

    deadline = time.monotonic() + max_wait_seconds
    while time.monotonic() < deadline:
        if is_reachable(base_url):
            return True
        time.sleep(poll_interval_seconds)

    return False
