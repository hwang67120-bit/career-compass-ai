# 실행 환경 연결 운영 규칙

상태: 로컬 실행 규칙 확정, 자동 점검과 상세 로그는 구현 예정

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
- [Docker Compose 서비스 시작 순서와 healthcheck](https://docs.docker.com/compose/how-tos/startup-order/)
- [Ollama Windows 환경변수와 네트워크 공개 설정](https://docs.ollama.com/faq)
