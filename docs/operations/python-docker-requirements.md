# Python 워커 Docker 환경 요구사항 (ai-python)

테스트 배포(서버, AWS 전)를 위해 `ai-python`을 컨테이너화할 때 필요한 환경을 정리한 **요구사항 문서**다. 실제 `Dockerfile`은 컨테이너 검토안(runbook)과 통일한 뒤 Python 담당(Claude)이 작성한다. Ollama는 이미지에 넣지 않고 별도 모델 머신을 `OLLAMA_BASE_URL`로 호출한다.

## 런타임
- 베이스: `python:3.10-slim` (uv 기반). `requires-python >=3.10`.
- 빌드: `pyproject.toml`·`uv.lock` 먼저 복사 → `uv sync --no-dev --locked` (프로덕션 의존성만, `dev` 그룹 제외). 로컬 `.venv`는 복사하지 않고 이미지 안에서 생성.
- 실행: uvicorn이 `.venv`에 설치되므로 **경로를 반드시 잡는다** — `uv run uvicorn app.main:app --host 0.0.0.0 --port 8000` 또는 `PATH=/app/.venv/bin:$PATH` 설정 후 `uvicorn ...`. (PATH를 안 잡으면 uvicorn을 못 찾음)
- 포트 **8000 — 컨테이너 내부 전용, host에 공개하지 않음**(브라우저 접근은 Java만).
- 작업 디렉터리 `/app`.

## 환경변수 (전부 런타임 주입 — 이미지/YAML에 실제 값 안 박음)
| 변수 | 비고 |
|---|---|
| `INTERNAL_SERVICE_TOKEN` | Java와 공유하는 내부 인증 토큰 |
| `OLLAMA_BASE_URL` | ⚠️ **별도 모델 머신 주소**(localhost 아님). 배포 핵심 설정 |
| `OLLAMA_MODEL` | 공고 직무명·기술 추출 |
| `OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL` | 공고 담당 업무 추출 |
| `OLLAMA_EVIDENCE_JUDGE_MODEL` | 근거 의미 비교(judge). settings 기본값 `qwen2.5:latest` |
| `OLLAMA_PROJECT_RESPONSIBILITY_MODEL` | 프로젝트 담당 업무 추출. settings 기본값 `qwen2.5:latest` |
| `OLLAMA_EMBEDDING_MODEL` | 임베딩(MVP 미사용, 설정 필수) |
| `GEMINI_API_KEY` / `GEMINI_MODEL` / `GEMINI_EMBEDDING_MODEL` | Gemini 폴백 |
| `JOB_POSTING_EXTRACTION_MAX_TEXT_LENGTH` | 정수 |

`OLLAMA_EVIDENCE_JUDGE_MODEL`·`OLLAMA_PROJECT_RESPONSIBILITY_MODEL`은 `.env.example`엔 없지만 `OllamaSettings`에 있는 값(기본 qwen2.5) — 배포 시 명시 권장. 실제 값은 secret/env로 주입.

## 헬스체크
- 엔드포인트: `GET /internal/v1/health` → `200 {"status":"UP","model_ready":false}` (`model_ready`는 모델 로딩까지 확인하지 않는 경량 값).
- ⚠️ **현재 이 엔드포인트는 `X-Internal-Token`을 요구**한다. healthcheck 명령에 토큰을 직접 넣으면 `docker inspect`·프로세스 인자에 노출된다 → **토큰 없는 별도 liveness 엔드포인트가 필요**(구현 항목).
- ⚠️ **`python:3.10-slim`에는 `curl`이 없다.** healthcheck에서 curl을 가정하지 말 것 — Python 기반 점검(예: `python -c "import urllib.request; ..."`)이나 curl 설치 중 택일.

## Ollama 자동실행 정책 (컨테이너에서 반드시 결정)
- 현재 Python startup(`app/main.py` lifespan → `ensure_ollama_running`)은 `OLLAMA_BASE_URL`이 응답하지 않으면 로컬 `ollama` 실행 파일을 찾아 **`ollama serve`를 subprocess로 자동 실행 시도**한다(로컬 개발 편의용).
- 컨테이너엔 ollama 바이너리가 없어 자동실행은 실패하고 서버 시작은 계속되지만, **원격 Ollama 장애를 "로컬 설치·PATH 문제"로 잘못 로깅**한다.
- **결정 필요**: 컨테이너/원격 모드에서 로컬 자동실행을 **끈다**(env 플래그 또는 `OLLAMA_BASE_URL`이 localhost가 아니면 skip). 원격 장애는 원격 문제로 로깅. → 구현 시 Python 코드(`ollama_process`/lifespan) 수정.

## 보안 (AWS 관례 참조)
- 컨테이너를 **비-root 유저**로 실행.
- 시크릿을 빌드 인자(`ARG`)·`ENV`·이미지 레이어에 넣지 않음(런타임 주입).
- `.dockerignore`로 `.venv/`, `.env`, `tests/`, `__pycache__/`, `.pytest_cache/`, `evaluation/` 제외.

## 통일·범위
- 공통 규약(베이스·비-root·헬스체크·env 주입·포트·멀티스테이지·태깅·`.dockerignore`)은 **컨테이너 검토안**(`runtime-connectivity-runbook.md`)에서 Java와 함께 정렬한다.
- 통합 `compose.yaml`은 **`deploy/` 공통 영역**에 둔다(`backend-java` 아님). Codex와 조율.
- **문서만.** 통일 후 실제 `Dockerfile`·`.dockerignore`는 Python 담당(Claude)이 작성. 서버 Docker 설치·env/secret·`compose up`·네트워크는 사용자.
