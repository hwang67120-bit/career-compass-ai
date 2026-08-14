# Python 워커 Docker 환경 요구사항 (ai-python)

테스트 배포(서버, AWS 전)를 위해 `ai-python`을 컨테이너화할 때 필요한 환경을 정리한 **요구사항 문서**다. 실제 `Dockerfile`은 Java 쪽 요구사항 문서와 함께 검토·공통 규약 통일 후 작성한다. Ollama는 이미지에 넣지 않고 별도 모델 머신을 `OLLAMA_BASE_URL`로 호출한다.

## 런타임
- 베이스: `python:3.10-slim` (uv 기반). `requires-python >=3.10`.
- 빌드: `uv sync --no-dev --locked` — 프로덕션 의존성만(pytest·mypy·ruff 등 `dev` 그룹 제외).
- 실행: `uvicorn app.main:app --host 0.0.0.0 --port 8000`
- 노출 포트: **8000**
- 작업 디렉터리: `/app` (ai-python 복사)

## 환경변수 (전부 런타임 주입 — 이미지/YAML에 실제 값 안 박음)
| 변수 | 비고 |
|---|---|
| `INTERNAL_SERVICE_TOKEN` | Java와 공유하는 내부 인증 토큰 |
| `OLLAMA_BASE_URL` | ⚠️ **별도 모델 머신 주소** (localhost 아님). 배포 핵심 설정 |
| `OLLAMA_MODEL` / `OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL` / `OLLAMA_EMBEDDING_MODEL` | 모델명 |
| `GEMINI_API_KEY` / `GEMINI_MODEL` / `GEMINI_EMBEDDING_MODEL` | Gemini 폴백 |
| `JOB_POSTING_EXTRACTION_MAX_TEXT_LENGTH` | 정수 |

실제 값은 secret/env로 주입한다. `.env.example` 참조.

## 헬스체크
- `GET /internal/v1/health` (헤더 `X-Internal-Token` 필요) → `200 {"status":"UP"}`
- compose healthcheck는 토큰 포함 curl로 구성 가능.

## 보안 (AWS 관례 참조 — [[feedback_reference_aws_conventions]] 정신)
- 컨테이너를 **비-root 유저**로 실행.
- 시크릿을 빌드 인자·이미지 레이어에 넣지 않음(런타임 주입).
- `.dockerignore`로 `.venv/`, `.env`, `tests/`, `__pycache__/`, `evaluation/` 등 제외.

## 통일 필요 — Java와 맞출 공통 규약 (검토 포인트)
두 문서 검토 시 아래를 정렬한다.
1. 베이스 이미지 전략 (slim vs distroless)
2. 비-root 유저 관례 (uid/gid, 이름)
3. 헬스체크 형식·간격
4. env/secret 주입 방식 (compose `env_file`? Docker secret?)
5. 포트 규약 (python=8000 / java=8080)
6. 멀티스테이지 빌드 여부 (빌더→런타임 슬림)
7. 이미지 태깅·`restart` 정책
8. `.dockerignore` 기준

## 범위
- **문서만.** 실제 `Dockerfile`은 Java 요구사항 문서와 통일 후 Claude가 작성.
- `compose.yaml`(backend-java, Codex 소유)에 java·python 서비스 엮는 것은 Codex와 조율.
- 서버의 Docker 설치·env/secret·`compose up`·네트워크는 사용자(서버 sysadmin).
