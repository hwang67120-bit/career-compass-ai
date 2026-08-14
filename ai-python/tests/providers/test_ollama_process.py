from app.providers.ollama_process import ensure_ollama_running, is_local_base_url
from app.providers.settings import OllamaSettings


def _settings() -> OllamaSettings:
    return OllamaSettings()


def test_is_local_base_url() -> None:
    assert is_local_base_url("http://127.0.0.1:11434") is True
    assert is_local_base_url("http://localhost:11434") is True
    assert is_local_base_url("http://100.86.24.127:11434") is False
    assert is_local_base_url("http://model-machine:11434") is False


def test_ensure_ollama_running_skips_spawn_when_base_url_remote() -> None:
    # 원격 base_url(컨테이너·배포)에서는 로컬 ollama serve를 시도하지 않는다.
    spawn_calls: list[str] = []

    result = ensure_ollama_running(
        _settings(),
        is_reachable=lambda base_url: False,
        is_local=lambda base_url: False,
        find_executable=lambda: "C:/fake/ollama.exe",
        spawn=lambda path: spawn_calls.append(path),
    )

    assert result is False
    assert spawn_calls == []


def test_ensure_ollama_running_skips_spawn_when_already_reachable() -> None:
    spawn_calls: list[str] = []

    result = ensure_ollama_running(
        _settings(),
        is_reachable=lambda base_url: True,
        find_executable=lambda: "C:/fake/ollama.exe",
        spawn=lambda path: spawn_calls.append(path),
    )

    assert result is True
    assert spawn_calls == []


def test_ensure_ollama_running_spawns_and_waits_until_reachable() -> None:
    spawn_calls: list[str] = []
    call_count = {"value": 0}

    def is_reachable(base_url: str) -> bool:
        call_count["value"] += 1
        # 처음 호출(스폰 여부 확인)은 실패, 이후 폴링에서 성공한다.
        return call_count["value"] > 2

    result = ensure_ollama_running(
        _settings(),
        is_reachable=is_reachable,
        find_executable=lambda: "C:/fake/ollama.exe",
        spawn=lambda path: spawn_calls.append(path),
        max_wait_seconds=1,
        poll_interval_seconds=0.01,
    )

    assert result is True
    assert spawn_calls == ["C:/fake/ollama.exe"]


def test_ensure_ollama_running_gives_up_after_timeout() -> None:
    result = ensure_ollama_running(
        _settings(),
        is_reachable=lambda base_url: False,
        find_executable=lambda: "C:/fake/ollama.exe",
        spawn=lambda path: None,
        max_wait_seconds=0.05,
        poll_interval_seconds=0.01,
    )

    assert result is False


def test_ensure_ollama_running_returns_false_when_executable_missing() -> None:
    spawn_calls: list[str] = []

    result = ensure_ollama_running(
        _settings(),
        is_reachable=lambda base_url: False,
        find_executable=lambda: None,
        spawn=lambda path: spawn_calls.append(path),
    )

    assert result is False
    assert spawn_calls == []
