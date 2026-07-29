# Java-Python 연결 방식

이 문서는 `AGENTS.md`의 `docs/architecture` 정의에 따라 삭제되지 않고 유지되는 아키텍처 설명이다. 개별 API의 요청·응답 스키마는 여기가 아니라 `contracts`에 둔다.

## 핵심 원칙

- `backend-java`와 `ai-python`은 항상 서로 다른 두 개의 서비스다. 같은 컴퓨터에서 실행 중이어도 포트가 다르면 서로 다른 목적지다.
- Java가 외부 요청을 받는 포트(예: 8080)와 Java가 Python으로 나가는 요청의 목적지는 서로 무관하다.
- Python의 실제 주소는 코드에 고정하지 않고 환경변수로 주입한다.

## 연결 값

- 환경변수: `PYTHON_WORKER_BASE_URL`
- 바인딩 위치: `backend-java/src/main/resources/application.yml`의 `python.worker.base-url`
- 설정 클래스: `com.careercompass.pythonworker.config.PythonWorkerProperties`
- 호출 클라이언트: `com.careercompass.pythonworker.client.PythonHealthClient` (Spring `RestClient` 사용)

## 환경별 값 예시

- 로컬 실행: `http://localhost:8000` (환경변수가 없을 때 사용하는 기본값, `application.yml`에 설정됨)
- Docker Compose로 함께 실행: `http://<python 서비스 이름>:8000` (컨테이너 이름으로 접근하며 실제 IP가 아니다)
- 배포 환경(AWS 등): 실제 내부 주소 또는 도메인 — 확인 필요, 배포 시점에 결정한다

## 호출 흐름

1. Java 부팅 시 `PYTHON_WORKER_BASE_URL`(없으면 기본값)을 읽어 `PythonWorkerProperties.baseUrl`에 저장한다.
2. `PythonHealthClient`가 이 `baseUrl`로 `RestClient`를 만든다.
3. 요청 시 경로만 추가로 붙여서(예: `/internal/v1/health`) 최종 목적지 주소가 완성된다.
4. Python(FastAPI/uvicorn)이 응답하면 Java가 DTO로 파싱한다.

## 연결 확인 (완료 판정 기준 2단계)

- Java와 Python을 동시에 실행한 상태에서 실제 HTTP 요청으로 데이터가 오가는지 확인한다.
- 한쪽만 단위 테스트를 통과했다고 연결이 검증된 것으로 보지 않는다.

## 새 API를 추가할 때

- 새로운 Java-Python 통신이 생기면 `contracts`에 요청·응답 형태를 먼저 반영한 뒤 양쪽 구현을 맞춘다.
- 이 문서는 갱신하지 않고 그대로 두되, 연결 구조 자체(주소 구성, 호출 방식)가 바뀌면 이 문서도 함께 갱신한다.

## 내부 서비스 인증 (2차 방어선)

네트워크 격리(Python 포트를 외부에 노출하지 않는 것)가 1차 방어선이고, 이 토큰 검증은 그게 뚫렸을 때를 대비한 2차 방어선이다. 최종 사용자 JWT와는 무관한 별개의 값이다.

- Python은 `ai-python/app/guardrails/internal_auth.py`의 `verify_internal_token`에서 모든 `/internal/v1/*` 요청에 대해 `X-Internal-Token` 헤더 값을 확인한다.
- 값이 없으면 422, 있지만 틀리면 401을 반환한다. 비교는 타이밍 공격을 막기 위해 `hmac.compare_digest`를 사용한다.
- Python 쪽 환경변수: `INTERNAL_SERVICE_TOKEN` (`ai-python/.env`). 충분히 길고 무작위한 값이어야 하며, JWT처럼 발급·서명·만료가 있는 값이 아니라 양쪽이 미리 공유하는 고정 비밀값이다.
- Java는 `python.worker.internal-token`을 `INTERNAL_SERVICE_TOKEN` 환경변수에서 읽는다.
- `PythonHealthClient`와 `PythonDocumentExtractionClient`는 모든 요청에 `X-Internal-Token`을 전송한다.
- 문서 추출 요청에는 Java가 생성한 UUID를 `X-Request-Id`로 보내고 Python 응답의 동일 식별자를 검증한다.
- 두 서버의 `INTERNAL_SERVICE_TOKEN` 값은 반드시 동일해야 한다.

이 값은 비밀정보이므로 코드나 커밋에 실제 값을 남기지 않고 환경변수로만 주입한다 (`AGENTS.md` 구현 원칙과 동일).

Java 클라이언트 단위 테스트와 실제 Java–Python HTTP 연결 검증은 Linux 개발 환경에서 실행해야 한다.
