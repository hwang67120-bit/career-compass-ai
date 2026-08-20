# 실행 환경 연결 운영 규칙

상태: 로컬 실행 규칙과 내부 통합 테스트 서버 운영 방향 확정, 자동 점검과 상세 로그는 구현 예정

이 문서는 Career Compass를 실행하거나 연결 오류를 조사할 때 사용하는 공통 운영 점검서다. Claude와 Codex를 포함한 모든 작업자는 실제 주소와 비밀정보를 문서나 대화에 복사하지 않고 이 문서의 자리표시자를 사용한다.

## 목적

- Java, Python과 Ollama의 실행 위치와 연결 방향을 고정한다.
- 주소와 시작 순서를 기억에 의존하지 않는다.
- 연결 실패 지점을 로그만 보고 구분할 수 있는 기준을 정의한다.
- 로컬 개발 규칙과 배포 후보 구성을 섞지 않는다.

## 주소와 비밀정보 표시 규칙

- 실제 사설 IP, 공인 IP, 호스트 이름, 인증키와 내부 토큰을 저장소 문서·PR·이슈·대화 예시에 기록하지 않는다.
- 실제 값 대신 `<OLLAMA_HOST>`, `<PYTHON_HOST>`, `<INTERNAL_SERVICE_TOKEN>`을 사용한다.
- 실제 연결 주소는 Git에서 제외된 로컬 환경변수에만 저장한다.
- 로그에는 실제 호스트 대신 `targetService`, `networkType`, `targetPort`를 남긴다.
- 인증키, 토큰, 요청 헤더, URL 쿼리 문자열과 채용공고 원문을 연결 진단 로그에 남기지 않는다.

## 확정된 로컬 실행 구조

| 구성요소 | 실행 위치 | 수신 포트 | 호출 대상 |
| --- | --- | ---: | --- |
| Java 백엔드 | Linux | `8080` | Python `127.0.0.1:8000` |
| Python 분석 서버 | Linux | `8000` | Windows Ollama `<OLLAMA_HOST>:11434` |
| Ollama 모델 서버 | Windows | `11434` | 외부 호출 없음 |

```text
브라우저
  -> Java(Linux:8080)
    -> Python(Linux:8000)
      -> Ollama(Windows:11434)
```

### 고정 환경변수

Java와 Python은 같은 Linux에서 실행되므로 loopback(자기 컴퓨터 내부 주소)을 사용한다.

```text
PYTHON_WORKER_BASE_URL=http://127.0.0.1:8000
```

Windows Ollama의 실제 주소는 `ai-python/.env`에만 작성하고 커밋하지 않는다.

```text
OLLAMA_BASE_URL=http://<OLLAMA_HOST>:11434
```

Linux Python의 접근을 허용해야 하는 로컬 개발 환경에서만 Windows Ollama에 다음 값을 사용한다.

```text
OLLAMA_HOST=0.0.0.0:11434
```

`<OLLAMA_HOST>`가 DHCP(자동 주소 할당)로 바뀌는 환경이면 공유기에서 주소 예약을 설정하거나 내부 DNS(내부 도메인 이름)를 사용한다. Ollama API에는 로컬 기본 인증이 없으므로 Windows 방화벽은 신뢰하는 개인 네트워크만 허용하고 공용 네트워크나 인터넷에 `11434`를 공개하지 않는다.

Java와 Python은 동일한 `INTERNAL_SERVICE_TOKEN`을 사용한다. 실제 토큰은 IntelliJ 실행 환경과 `ai-python/.env`에만 저장하고, 변경 뒤 두 프로세스를 모두 재시작한다. 토큰 값은 화면과 로그에 출력하지 않는다.

## 내부 통합 테스트 서버 구조

내부 통합 테스트 서버에는 Java, Python API와 PostgreSQL만 실행한다. Ollama는 서버 설치·CI
대상에서 제외하고 별도 컴퓨터에서 필요할 때 수동으로 실행한다.

| 구성요소 | 실행 위치 | 수신 범위 | 호출 대상 |
| --- | --- | --- | --- |
| Java 백엔드 | 내부 테스트 서버 | 신뢰하는 내부망 `8080` | Python `127.0.0.1:8000`, PostgreSQL `127.0.0.1:5432` |
| Python 분석 API | 내부 테스트 서버 | loopback `8000` | 외부 Ollama `<OLLAMA_HOST>:11434` |
| PostgreSQL | 내부 테스트 서버 | loopback `5432` | 외부 호출 없음 |
| Ollama | 별도 컴퓨터 | 신뢰하는 내부망 `11434` | 외부 호출 없음 |

```text
브라우저
  -> Java(내부 테스트 서버:8080)
    -> Python(내부 테스트 서버:8000)
      -> Ollama(별도 컴퓨터:11434, 수동 실행)
    -> PostgreSQL(내부 테스트 서버:5432)
```

- 서버의 Python은 Java에서만 호출하므로 기본 바인딩 주소를 `127.0.0.1`로 사용한다.
- PostgreSQL은 서버 밖에 공개하지 않는다.
- 서버 방화벽은 SSH와 브라우저 테스트용 Java 포트만 신뢰하는 내부망에서 허용한다.
- 서버에는 `11434` 포트를 열거나 Ollama 자동 실행 서비스를 만들지 않는다.
- Ollama가 꺼져 있어도 Java·Python 프로세스는 시작할 수 있어야 한다. 실제 모델 분석은
  `MODEL_UNAVAILABLE`로 구분하고, 단위 테스트 실패로 오인하지 않는다.

### 서버 주소 관리

- 실제 서버 IP와 SSH 사용자 이름은 저장소에 기록하지 않고 로컬 SSH 설정에서만 관리한다.
- DHCP(자동 주소 할당)를 사용하면 공유기에서 MAC 주소 기반 예약을 적용한다.
- 문서와 스크립트는 실제 IP 대신 `<TEST_SERVER_HOST>`를 사용한다.
- SSH 공개키만 사용하고 개인키, 로그인 비밀번호와 등록용 토큰을 서버 저장소에 복사하지 않는다.

```sshconfig
Host career-test-server
    HostName <TEST_SERVER_HOST>
    User <TEST_SERVER_USER>
    IdentityFile ~/.ssh/id_ed25519
```

### 현재 저장소 준비 상태

- `backend-java/compose.yaml`에는 PostgreSQL만 있고 healthcheck는 아직 없다.
- Java와 Python용 Dockerfile(도커 이미지 생성 파일)과 통합 Compose 서비스는 아직 없다.
- GitHub 호스팅 실행기의 Java·Python 단위 CI는 있으며, 서버 통합 workflow(자동 검증 절차)는 아직 없다.
- 서버의 앱 자동 시작과 롤백 구성은 아직 없다.

최초 서버 확인에서는 기존 실행 도구로 Java·Python을 수동 실행하고 PostgreSQL만 Compose로
시작한다. 위 누락 항목을 구현하고 검증하기 전에는 자동 배포가 준비됐다고 표시하지 않는다.

## 컨테이너 환경 파일 검토안 — 실제 파일 구현 전

상태: **제안**. 이 절은 필요한 파일과 책임을 검토하기 위한 문서이며 Dockerfile, Compose와
환경 파일을 아직 확정하거나 구현하지 않는다. 사용자 검토 뒤 구성을 하나로 통일하고 Python
담당이 실제 파일을 작성한다. Codex는 이 단계에서 배포 파일을 수정하지 않는다.

### 파일 후보와 책임

| 영역 | 파일 후보 | 책임 |
| --- | --- | --- |
| Java | `backend-java/Dockerfile` | JDK 21로 빌드하고 JRE 21 실행 이미지를 만든다. |
| Java | `backend-java/.dockerignore` | `.gradle`, `build`, `.idea`, `.env`, 로그와 로컬 생성물을 제외한다. |
| Python | `ai-python/Dockerfile` | Python 3.10과 잠긴 `uv.lock`으로 FastAPI 실행 이미지를 만든다. |
| Python | `ai-python/.dockerignore` | `.venv`, `__pycache__`, `.pytest_cache`, `.env`와 평가 로그를 제외한다. |
| 공통 | `deploy/compose.yaml` | Java, Python과 PostgreSQL의 네트워크·상태 점검·시작 순서·로그 회전을 통일한다. |
| 공통 | `deploy/.env.example` | 실제 값 없이 서버 환경변수 이름과 자리표시자만 제공한다. |

- 기존 `backend-java/compose.yaml`과 새 통합 Compose를 함께 운영하지 않는다.
- 서버용 환경변수 이름은 `deploy/.env.example` 한 곳에 모으고 실제 `deploy/.env`는 Git에서 제외한다.
- 프론트 이미지, Ollama 이미지, 자동 배포 workflow와 모니터링 도구는 이번 범위가 아니다.

### Java 이미지 요구사항

- build(빌드)와 runtime(실행)을 분리하는 multi-stage build(다단계 빌드)를 사용한다.
- 기준은 Java 21과 Gradle 8.14다. 현재 `gradle-wrapper.properties`만 있고 `gradlew`와
  `gradle-wrapper.jar`가 없으므로 구현 전에 다음 중 하나를 확정한다.
  1. CI와 같은 `gradle:8.14-jdk21` 빌더 이미지를 사용한다.
  2. Gradle Wrapper 전체 파일을 복원한 뒤 `./gradlew`로 빌드한다.
- 실행 이미지는 JRE 21만 포함하고 빌드 도구, 소스와 Gradle cache(캐시)를 포함하지 않는다.
- non-root(관리자 권한이 아닌 사용자)로 실행한다.
- 비밀번호, OAuth secret, API 키와 내부 토큰을 이미지의 `ARG`, `ENV`, layer(이미지 계층)에 넣지 않는다.
- Java는 컨테이너 안에서 `8080`을 수신하고 `/actuator/health`를 상태 점검 후보로 사용한다.
- Python과 DB는 컨테이너 loopback이 아니라 Compose 서비스 DNS를 사용한다.

```text
PYTHON_WORKER_BASE_URL=http://ai-python:8000
DB_HOST=postgres
DB_PORT=5432
```

### Python 이미지 요구사항

- Python 3.10 slim 기반을 사용하고 `uv` 버전 또는 이미지 digest(내용 식별값)를 고정한다.
- `pyproject.toml`과 `uv.lock`을 먼저 복사해 `uv sync --locked --no-dev`로 실행 의존성만 설치한다.
- 로컬 `.venv`를 복사하지 않고 이미지 안에서 새로 만든다.
- non-root 사용자로 실행하고 애플리케이션과 가상환경만 포함한다.
- Uvicorn은 컨테이너 내부에서 `0.0.0.0:8000`을 수신하되 host(호스트)에는 공개하지 않는다.
- Ollama 실행 파일과 모델을 이미지에 설치하지 않는다. `OLLAMA_BASE_URL`은 별도 모델 컴퓨터를 가리킨다.
- Ollama가 꺼져 있어도 Python API는 시작돼야 하며 원격 장애를 로컬 PATH 문제로 기록하지 않아야 한다.

### 통합 Compose 요구사항

- 서비스 이름 후보는 `backend-java`, `ai-python`, `postgres`다. Ollama 서비스는 만들지 않는다.
- Python과 PostgreSQL 포트는 host에 공개하지 않고 Java만 내부망 브라우저 접근을 허용한다.
- 실제 IP 대신 `JAVA_BIND_ADDRESS`와 서버 방화벽으로 공개 범위를 제한한다.
- PostgreSQL 데이터는 named volume(이름이 있는 영속 볼륨)에 저장한다.
- PostgreSQL과 Python 상태 점검 뒤 Java를 시작하도록 의존 순서를 검증한다.
- `restart: unless-stopped` 후보를 사용하되 종료 원인과 직전 로그가 가려지지 않게 한다.
- 각 서비스 로그에 크기와 보관 파일 개수 제한을 둔다.
- `INTERNAL_SERVICE_TOKEN`은 Java와 Python에 같은 값을 주입하되 파일과 로그에 값을 기록하지 않는다.

### 서버 환경변수 분리

| 구분 | 환경변수 |
| --- | --- |
| 공통 비밀값 | `INTERNAL_SERVICE_TOKEN` |
| Java DB | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` |
| Java 내부 연결 | `PYTHON_WORKER_BASE_URL` |
| Java 외부 연동 | `PUBLIC_EMPLOYMENT_API_SERVICE_KEY`, `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET` |
| Java 운영 | `GITHUB_API_CONNECT_TIMEOUT`, `GITHUB_API_READ_TIMEOUT`, `SESSION_COOKIE_SECURE` |
| Python Ollama | `OLLAMA_BASE_URL`, `OLLAMA_MODEL`, `OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL`, `OLLAMA_EVIDENCE_JUDGE_MODEL`, `OLLAMA_PROJECT_RESPONSIBILITY_MODEL`, `OLLAMA_EMBEDDING_MODEL` |
| Python Gemini | `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_EMBEDDING_MODEL` |
| Python 입력 제한 | `JOB_POSTING_EXTRACTION_MAX_TEXT_LENGTH` |

- 실제 키, 토큰, 비밀번호와 서버 주소는 예시 파일에 넣지 않는다.
- 기본값은 Java 설정과 Python `BaseSettings`를 기준으로 검증하며 사용하지 않는 필수값을 임의 삭제하지 않는다.
- `ai-python/.env.example`에 빠진 모델 변수는 통합 환경 파일 작성 시 실제 설정 클래스와 대조한다.

### 구현 승인 전 결정할 항목

1. Java가 고정 Gradle 빌더를 사용할지 Wrapper를 먼저 복원할지
2. Java host 바인딩 기본값과 내부망 방화벽 범위
3. Python 상태 점검이 내부 토큰을 노출하지 않고 실행될 방법
4. Python 원격 Ollama 모드에서 로컬 자동 실행을 시도하지 않게 할 방법
5. 기반 이미지를 tag(태그)로 고정할지 digest까지 고정할지
6. 기존 `backend-java/compose.yaml`을 이동할지 통합 파일로 대체할지

### 실제 파일 구현 후 검증

1. 비밀값 없이 `docker compose config`가 성공한다.
2. Java와 Python 이미지를 깨끗한 캐시에서 각각 빌드한다.
3. Ollama를 끈 상태에서 PostgreSQL, Python과 Java가 기동하고 상태가 구분된다.
4. Flyway 마이그레이션과 Java→Python 내부 토큰 요청이 성공한다.
5. 별도 모델 컴퓨터를 켠 뒤 서버→Ollama 연결과 실제 분석 요청을 확인한다.
6. 재시작 뒤 DB 데이터, 자동 시작과 로그 회전을 확인한다.
7. 브라우저에서 분석 단계와 단계별 실패 표시를 확인한다.

### 구현 분담

- Python 담당: 사용자 승인 뒤 Java·Python Dockerfile, `.dockerignore`, 통합 Compose와 환경변수 예시를 작성한다.
- Codex: 컨테이너 파일을 동시에 수정하지 않고 남은 백엔드 도메인 API 명세와 시나리오를 작성한다.
- 공통: Docker 변경 PR에서 양쪽 이미지 빌드와 Compose 실제 기동 결과를 함께 검토한다.

## CI(지속적 통합)와 서버 반영 원칙

초기 CI는 GitHub가 제공하는 실행기를 사용한다. 내부 테스트 서버는 검증되지 않은 PR 코드를
직접 실행하는 self-hosted runner(자체 호스팅 실행기)로 등록하지 않는다.

### PR과 develop 검증

| 시점 | 실행 위치 | 검증 범위 | 실제 Ollama |
| --- | --- | --- | --- |
| PR 생성·갱신 | GitHub 기본 Linux 실행기 | Java 컴파일·단위·API·계약 테스트, Python 문법·단위·계약 테스트 | 사용 안 함 |
| `develop` 병합 | GitHub 기본 Linux 실행기 | PR과 동일한 검증 재실행 | 사용 안 함 |
| 서버 반영 후 | 내부 테스트 서버 | PostgreSQL 마이그레이션, Java-Python 실제 연결, 브라우저 흐름 | 필요할 때 수동 실행 |

- CI의 LLM 테스트는 mock(모의 객체)이나 고정 fixture(고정 테스트 자료)를 사용한다.
- API 키, DB 비밀번호와 `INTERNAL_SERVICE_TOKEN`은 GitHub Actions Secret 또는 서버의
  Git 제외 환경 파일로만 주입한다.
- 외부 공공데이터와 실제 Ollama를 사용하는 테스트는 CI 필수 통과 조건으로 두지 않는다.
- 초기 서버 반영은 `develop` CI 성공을 확인한 뒤 수동으로 수행한다.
- 자동 배포는 반복 가능한 시작·중지·헬스체크·롤백 절차가 검증된 뒤 별도 확정한다.

## 서버 최초 준비 체크리스트

다음 절차는 내일 서버에서 순서대로 실행한다. 오늘은 명령을 실행하거나 서버 설정을 변경하지 않는다.

### 1. 읽기 전용 사전 점검

```bash
hostnamectl
hostname -I
free -h
df -h /
git --version
docker --version
docker compose version
java -version
python3 --version
uv --version
systemctl is-active ssh
```

설치되지 않은 프로그램은 이 결과를 확인한 뒤 필요한 항목만 설치한다. Java 실행 기준은 21이며,
Python 버전은 `ai-python/pyproject.toml`과 `uv.lock`을 기준으로 맞춘다.

### 2. 배치 경로와 소스 준비

- 앱 전용 경로 `<APP_DIR>`를 정하고 개인 홈의 임시 폴더와 구분한다.
- 서버에는 Git 저장소만 두고 Windows 공유 폴더를 실행 경로로 사용하지 않는다.
- 최초에는 `develop`의 검증된 commit SHA를 명시적으로 checkout한다.
- 서버에서 기능 코드를 직접 수정하거나 커밋하지 않는다.
- `.env`, 로그와 데이터 디렉터리는 Git 추적 대상에서 제외한다.

```bash
git clone <REPOSITORY_URL> <APP_DIR>
cd <APP_DIR>
git fetch origin
git checkout --detach <VERIFIED_COMMIT_SHA>
```

### 3. 환경변수 준비

실제 값은 화면 공유, 문서, PR과 일반 로그에 출력하지 않는다.

```text
# Java
DB_HOST=127.0.0.1
DB_PORT=<DB_PORT>
DB_NAME=<DB_NAME>
DB_USERNAME=<DB_USERNAME>
DB_PASSWORD=<DB_PASSWORD>
PYTHON_WORKER_BASE_URL=http://127.0.0.1:8000
INTERNAL_SERVICE_TOKEN=<INTERNAL_SERVICE_TOKEN>
PUBLIC_EMPLOYMENT_API_SERVICE_KEY=<PUBLIC_EMPLOYMENT_SERVICE_KEY>
GITHUB_API_CONNECT_TIMEOUT=3s
GITHUB_API_READ_TIMEOUT=10s
GITHUB_OAUTH_CLIENT_ID=<GITHUB_OAUTH_CLIENT_ID>
GITHUB_OAUTH_CLIENT_SECRET=<GITHUB_OAUTH_CLIENT_SECRET>
SESSION_COOKIE_SECURE=<true_or_false>

# Python
INTERNAL_SERVICE_TOKEN=<INTERNAL_SERVICE_TOKEN>
OLLAMA_BASE_URL=http://<OLLAMA_HOST>:11434
OLLAMA_MODEL=<ANALYSIS_MODEL>
OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL=<RESPONSIBILITY_MODEL>
OLLAMA_EVIDENCE_JUDGE_MODEL=<EVIDENCE_JUDGE_MODEL>
OLLAMA_PROJECT_RESPONSIBILITY_MODEL=<PROJECT_RESPONSIBILITY_MODEL>
OLLAMA_EMBEDDING_MODEL=<EVALUATION_ONLY_EMBEDDING_MODEL>
GEMINI_API_KEY=<GEMINI_API_KEY>
GEMINI_MODEL=<GEMINI_ANALYSIS_MODEL>
GEMINI_EMBEDDING_MODEL=<EVALUATION_ONLY_EMBEDDING_MODEL>
```

Java와 Python의 `INTERNAL_SERVICE_TOKEN`은 반드시 같아야 한다. Ollama 주소는 서버 주소가
아니라 별도 모델 컴퓨터의 내부 주소다. 현재 Python 설정은 임베딩을 MVP 분석에서 호출하지
않더라도 `OLLAMA_EMBEDDING_MODEL` 값을 시작 시 요구하므로 누락하지 않는다.

### 4. 실행 순서

1. PostgreSQL을 시작하고 healthcheck(상태 점검)가 통과하는지 확인한다.
2. Python을 `127.0.0.1:8000`으로 시작한다.
3. Java를 `8080`으로 시작한다.
4. Java 자체 상태와 Java-Python 연결 상태를 각각 확인한다.
5. 모델 테스트가 필요할 때만 별도 컴퓨터에서 Ollama를 수동 실행한다.
6. 서버에서 Ollama 접근을 확인한 뒤 브라우저 분석을 실행한다.

Ollama가 꺼진 상태에서도 1~4단계는 점검할 수 있어야 한다.

### 5. 단계별 완료 조건

- PostgreSQL: 컨테이너가 healthy이고 Flyway 마이그레이션 오류가 없다.
- Python: 상태 API가 응답하고 내부 인증 실패와 모델 미준비를 구분한다.
- Java: Actuator 상태 API가 응답하고 Python 연결 결과를 별도로 표시한다.
- 실제 연결: 같은 예제 JSON으로 Java→Python 요청·응답 계약을 확인한다.
- 브라우저: 검색, 공고 추출, 프로젝트 근거 추출, 사용자 확인과 결과 조회 상태를 구분한다.

다음 상황에서는 다음 단계로 넘어가지 않는다.

- Git 작업 폴더에 추적되지 않은 비밀 파일이나 예상하지 않은 수정이 있음
- DB 마이그레이션 실패
- Java와 Python의 내부 토큰 불일치
- Python 요청·응답 계약 위반
- 실제 IP, 토큰, API 키 또는 원문 데이터가 로그에 노출됨

## 서버 운영 중 갱신·복구

- 반영 전 현재 실행 commit SHA와 DB 마이그레이션 버전을 기록한다.
- 새 commit은 CI 성공을 확인한 뒤 적용한다.
- 애플리케이션 반영 실패 시 이전 검증 commit으로 돌아가되, 적용된 Flyway 파일을 임의로
  삭제하거나 DB를 과거 상태로 강제 되돌리지 않는다.
- DB 구조 변경이 포함되면 백업과 복구 검증 전 자동 배포를 적용하지 않는다.
- 프로세스 로그는 Java, Python과 PostgreSQL을 분리하고 토큰과 원문을 남기지 않는다.

## 고정 실행 순서

1. Windows에서 Ollama를 실행한다.
2. Windows 로컬에서 Ollama API 응답을 확인한다.
3. Linux에서 `<OLLAMA_HOST>:11434` 접근을 확인한다.
4. Linux에서 Python을 `0.0.0.0:8000`으로 실행한다.
5. Linux에서 Java를 실행한다.
6. Java 상태, Java-Python 연결과 브라우저 분석 흐름을 순서대로 확인한다.

```bash
ss -ltnp | rg ':8080|:8000'
curl http://<OLLAMA_HOST>:11434/api/tags
```

`11434`는 Windows에서 실행되므로 Linux의 `ss` 결과에 나타나지 않아도 된다. Linux에서는 `curl` 응답으로 확인한다.

## 상태값의 의미

| 필드 | 한글 뜻 | 판정 기준 |
| --- | --- | --- |
| `connected` | Python 연결 여부 | Java가 Python 헬스 응답을 계약대로 받음 |
| `status=UP` | Python 서버 정상 | Python 프로세스와 내부 API가 응답함 |
| `modelReady=true` | 분석 모델 준비 완료 | Python이 Ollama와 현재 분석에 필요한 모델을 확인함 |
| `modelReady=false` | 분석 모델 준비 안 됨 | Python은 응답하지만 Ollama 연결 또는 필수 모델 확인이 실패함 |

Java의 `/actuator/health`가 `UP`이어도 Python과 Ollama까지 모두 정상이라는 뜻은 아니다. Java 상태, Python 연결과 `modelReady`를 함께 확인한다. MVP(최소 기능 제품)에서 임베딩 호출을 사용하지 않으므로 임베딩 전용 모델은 필수 모델 판정에서 제외한다.

## 연결 실패 분류

### Java 로그

| 실패 코드 | 한글 뜻 | 대표 원인 |
| --- | --- | --- |
| `CONNECTION_REFUSED` | 연결 거부 | Python 종료 또는 잘못된 포트 |
| `CONNECT_TIMEOUT` | 연결 시간 초과 | 잘못된 주소, 방화벽 또는 네트워크 문제 |
| `READ_TIMEOUT` | 응답 시간 초과 | Python 또는 모델 처리 지연 |
| `UNAUTHORIZED` | 내부 인증 실패 | 양쪽 내부 토큰 불일치 |
| `INVALID_RESPONSE` | 응답 계약 오류 | 빈 응답, 파싱 실패 또는 필수 필드 누락 |

### Python 로그

| 실패 코드 | 한글 뜻 | 대표 원인 |
| --- | --- | --- |
| `OLLAMA_UNAVAILABLE` | Ollama 연결 불가 | Ollama 종료, 주소 변경 또는 방화벽 차단 |
| `MODEL_NOT_READY` | 필수 모델 준비 안 됨 | 설정한 모델이 설치 목록에 없음 |
| `OLLAMA_TIMEOUT` | Ollama 응답 시간 초과 | 모델 서버 지연 또는 자원 부족 |
| `OLLAMA_INVALID_RESPONSE` | Ollama 응답 오류 | HTTP 오류 또는 예상하지 않은 응답 |

## 로그 작성 기준

```text
python_health_check_completed connected=true modelReady=true durationMs=24
python_health_check_failed targetService=PYTHON_WORKER networkType=LOOPBACK targetPort=8000 failureCode=CONNECTION_REFUSED rootCause=ConnectException durationMs=12
ollama_connectivity_check_failed targetService=OLLAMA networkType=PRIVATE_NETWORK targetPort=11434 failureCode=OLLAMA_UNAVAILABLE durationMs=2004
```

필수 필드는 `requestId`, `targetService`, `networkType`, `targetPort`, `failureCode`, 필요한 경우의 `httpStatus`, 비밀정보를 포함하지 않는 `rootCause`, `durationMs`다. 실제 주소, 전체 URL, 내부 토큰과 응답 원문은 기록하지 않는다. 전체 스택 트레이스는 예상하지 못한 서버 오류에만 사용한다.

## 구현 예정 항목

다음 항목은 방향만 확정했으며 아직 완료된 기능으로 보지 않는다.

1. `contracts`: Python 헬스 응답의 현재 필드와 의미를 계약 문서로 고정한다.
2. `ai-python`: Ollama 연결과 필수 분석 모델을 확인해 `model_ready`를 반환한다.
3. `ai-python`: 원격 Ollama 환경에서 자동 실행 실패를 PATH 문제로 잘못 안내하지 않도록 시작 로그를 분리한다.
4. `backend-java`: 연결 거부, 시간 초과, 인증 실패와 응답 오류를 안정적인 실패 코드로 분류한다.
5. `scripts`: 비밀값 없이 Java, Python, 내부 토큰, Ollama와 필수 모델을 검사하는 Linux 사전 점검 스크립트를 추가한다.
6. `deploy`: 배포 구성이 확정되면 서비스 헬스체크와 시작 의존성을 추가한다.

API 필드나 상태값을 변경한다면 `contracts`를 먼저 수정하고 Java와 Python 계약 테스트를 함께 변경한다.

## 배포 환경 후보 — 확인 필요

배포에서는 실제 IP를 고정하지 않고 서비스 DNS를 우선 검토한다.

```text
Java   -> http://ai-python:8000
Python -> http://ollama:11434
```

아직 확정 정책이 아니다. 배포 서버, GPU 제공 방식과 Ollama 실행 위치를 결정한 뒤 확정한다. Docker Compose를 사용한다면 `healthcheck`와 `depends_on.condition=service_healthy`를 함께 검증한다.

## 완료 판정

- Java, Python, 내부 인증, Ollama와 필수 모델의 상태를 각각 구분한다.
- 연결을 하나씩 끊었을 때 해당 실패 코드가 로그에 남는다.
- 실제 IP, 인증키, 내부 토큰과 원문 데이터가 문서·로그·테스트 결과에 노출되지 않는다.
- Java·Python 단위 테스트, 계약 테스트, Linux 연결 테스트와 브라우저 테스트를 통과한다.

## 공식 근거

- [Spring Boot Actuator와 사용자 정의 HealthIndicator](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- [Spring Boot Dockerfile과 계층 이미지](https://docs.spring.io/spring-boot/reference/packaging/container-images/dockerfiles.html)
- [uv의 Docker 사용법](https://docs.astral.sh/uv/guides/integration/docker/)
- [Docker 다단계 빌드](https://docs.docker.com/build/building/multi-stage/)
- [Docker Compose 서비스 시작 순서와 healthcheck](https://docs.docker.com/compose/how-tos/startup-order/)
- [Ollama Windows 환경변수와 네트워크 공개 설정](https://docs.ollama.com/faq)
- [GitHub Actions 자체 호스팅 실행기 보안](https://docs.github.com/en/actions/reference/security/secure-use)
- [GitHub Actions workflow 문법](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
