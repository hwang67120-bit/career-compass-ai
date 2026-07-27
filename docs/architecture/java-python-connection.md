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
