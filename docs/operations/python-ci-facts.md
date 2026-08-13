# Python CI 팩트 (ai-python)

self-hosted runner CI 준비를 위한 Python 쪽 사실 정리(2026-08-14 코드 기준). Codex의 CI 문서·워크플로 작성 시 참조한다.

## 실행 환경
- Python **3.10+** (`pyproject.toml: requires-python = ">=3.10"`)
- 패키지 관리 **uv** (`[project]` + `[dependency-groups]` 형식)
- 설치: `uv sync` (dependency-groups의 pytest·pytest-asyncio·pytest-cov 포함 — 정확한 플래그는 uv 버전 확인)
- 실행: `pytest` (작업 디렉터리 `ai-python/`)

## 마커 현황
- 정의된 마커: `real_gemini` 하나뿐
- `pyproject.toml: addopts = -m "not real_gemini"` → **기본 pytest는 real_gemini 제외** (Gemini API 키 불필요)
- `real_gemini` 테스트는 `GEMINI_API_KEY` 필요, `pytest -m real_gemini`로만 실행 → **CI 기본 잡에서 제외 유지 권장**

## 핵심 — 실제 Ollama 필요 테스트가 마커로 안 걸러짐
기본 `pytest`가 **live Ollama(127.0.0.1:11434 + qwen2.5 모델)** 를 요구하는 테스트를 그대로 실행한다. 마커 없음:

| 위치 | 내용 |
|---|---|
| `tests/providers/test_ollama.py` | `provider` fixture가 기본 base_url로 실제 `httpx.AsyncClient` 생성 → 이 fixture 쓰는 테스트 전부 live |
| `tests/job_postings/test_job_postings_extract.py:103,140` | `test_extract_succeeds_with_real_ollama`(+gemini 미설정 변형) |

runner에 Ollama가 없으면 "단위" 잡도 실패한다(전체 스위트 ~163초 소요 원인). 참고: mock 테스트(`test_job_evidence_similarities`, `test_project_responsibility_extractions` 등)는 `dependency_overrides`로 가짜 provider를 써서 live가 불필요하다.

## CI 설계 선택지 (하나 확정 필요)
- **(a) `real_ollama` 마커 도입** — 위 live 테스트에 마커 달고 `addopts`에 `and not real_ollama` 추가. 단위 잡은 Ollama 없이 통과, 통합 잡은 `pytest -m real_ollama`로 Ollama 있는 runner에서 실행(`real_gemini`와 같은 패턴).
- **(b) runner에 Ollama 상주** — 서버에 Ollama+qwen2.5 두고 전체 스위트 실행. 느리지만 마커 작업 없음.

이 Ollama 분리는 Claude·Codex 공동 인지 사항이다. 방식(a/b) 확정 후 **마커 변경은 Claude(ai-python 담당)가 반영**한다.

## 보안 — 공개 repo + self-hosted runner (설계 전제)
이 repo는 **PUBLIC**이다. 공개 repo에서 self-hosted runner를 fork PR로 실행하면 **서버에서 임의 코드 실행**이 가능하다(GitHub 공식 경고). 포트폴리오 목적상 공개는 유지하므로, runner 전략으로 막는다.

- **PR·단위 테스트 = GitHub 호스팅 runner** (공개 repo 무료). 서버를 안 쓰므로 fork PR이 서버에 닿지 못한다.
- **서버 self-hosted runner = 통합 테스트만**, 트리거는 **`push`(develop/main)·`workflow_dispatch`(수동)만**. `pull_request`(특히 fork)로는 실행하지 않는다.
- GitHub 설정: repo → Settings → Actions → **Fork PR은 외부 기여자 승인 필요** ON.

서버 하드닝(사용자 sysadmin 범위, 저장소 밖):
- 공유기 **포트포워딩 없음** 확인(LAN `192.168.0.88`+Tailscale라 미포워딩 시 인터넷 미노출)
- **UFW** 인바운드 기본 차단, SSH만(가능하면 LAN/Tailscale 한정)
- **Postgres·Ollama는 localhost/Tailscale 바인딩**(0.0.0.0 금지, 기본 인증 없음)
- SSH 키 방식·비밀번호 로그인 off
- 시크릿은 `.env`(gitignore)·GitHub Actions Secrets에만, 로그·커밋 금지
- runner는 **비-root 전용 유저**, `unattended-upgrades`로 OS 보안 패치

## Python 잡 runner prereq
- 단위 잡(선택지 a): **Python 3.10+, uv, git** (Ollama 불필요)
- 통합 잡(또는 선택지 b): 위 + **Ollama + `qwen2.5:latest`** (그리고 `OLLAMA_MODEL=exaone3.5:latest` 경로 테스트가 있으면 exaone3.5도)

## 담당
`.github/workflows`는 공용이다. Python 잡=Claude, Java 잡=Codex. 파일 하나로 두되 각자 자기 잡만 수정한다.
