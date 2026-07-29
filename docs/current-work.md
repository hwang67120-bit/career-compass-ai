# 현재 작업 상태

이 문서는 Java·Python·공통 계약 작업의 현재 위치와 검증 수준을 공유한다.
`구현 완료`라는 표현 대신 실제로 통과한 가장 높은 검증 상태를 기록한다.

- 마지막 확인일: 2026-07-29
- Python 기준: `origin/python` 커밋 `42dd375`
- Java 기준: `java` 브랜치 작업 트리
- 상태 정의: 루트 `AGENTS.md`의 완료 판정 기준

## 기록 규칙

- 담당자는 작업 시작 전 자신의 영역과 파일을 기록한다.
- 각 담당자는 자신이 실행한 검증만 상태 근거로 사용한다.
- 외부 제공자 실제 호출은 제공자 단위 검증이며 Java–Python 통합 검증이 아니다.
- 계약 변경은 `제안` 또는 `확인 필요`로 표시하고 사용자 확정 전 구현 기준으로 사용하지 않는다.
- 상태가 바뀌면 근거가 되는 테스트·HTTP 요청·브라우저 확인·배포 환경을 함께 기록한다.

## 현재 담당과 편집 범위

| 영역 | 담당 | 현재 편집 범위 | 충돌 방지 |
| --- | --- | --- | --- |
| Java 인증 | Codex | `backend-java`의 GitHub OAuth, 인증 응답, 보안 설정과 테스트 | 현재 작업을 전달하기 전 같은 파일을 수정하지 않음 |
| Python 분석 | Python 담당 AI | `ai-python`의 제공자·PDF·임베딩·유사도·재정렬 | Java 담당은 직접 수정하지 않고 계약 영향만 보고 |
| 공통 계약 | 사용자 확인 후 지정 담당 | `contracts/*` | `제안`을 사용자 확인 없이 `MVP 확정`으로 바꾸지 않음 |
| 공통 문서 | 사용자 또는 지정 담당 | `README.md`, `AGENTS.md`, `docs/current-work.md` | 한 시점에 한 담당자만 공통 영역 수정 |

## Python 현재 검증 상태

아래 표는 `origin/python` 커밋 `42dd375`의 코드·README·테스트 파일을 확인한 결과다.
Java와 Python 서버를 함께 실행한 계약 테스트가 없으므로 `INTEGRATION_TESTED` 이상인 기능은 없다.

| 기능 | 구현 위치 | 현재 상태 | 확인 근거 | 다음 단계 |
| --- | --- | --- | --- | --- |
| 상태 확인 API | `app/health/`, `tests/health/test_health.py` | `UNIT_TESTED` | FastAPI 라우트와 Python API 테스트 존재 | Java에서 실제 HTTP 호출 후 계약 확인 |
| 내부 서비스 토큰 검사 | `app/guardrails/internal_auth.py` | `UNIT_TESTED` | 누락·오류·정상 토큰 테스트 존재 | Java 헤더 전송 구현 후 실제 서버 연결 |
| PDF 페이지별 텍스트 추출 | `app/services/pdf_extraction.py` | `UNIT_TESTED` | `tests/services/test_pdf_extraction.py` 존재 | 추출 API 라우트와 개인정보 제거 연결 |
| Ollama 구조화 추출 | `app/providers/ollama.py` | `UNIT_TESTED` | 로컬 Ollama 실제 호출 테스트 존재 | 추출 서비스와 HTTP API에 연결 |
| Gemini 구조화 추출 | `app/providers/gemini.py` | `UNIT_TESTED` | 가상 채용공고를 사용한 실제 Gemini 호출 테스트 존재 | 추출 서비스와 HTTP API에 연결 |
| Ollama 임베딩 | `app/providers/embedding.py` | `UNIT_TESTED` | `tests/providers/test_ollama_embedding.py` 존재 | 분석 실행 API에서 호출 |
| Gemini 임베딩 | `app/providers/embedding.py` | `UNIT_TESTED` | `tests/providers/test_gemini_embedding.py` 존재 | 분석 실행 API에서 호출 |
| 코사인 유사도 | `app/services/similarity.py` | `UNIT_TESTED` | `tests/services/test_similarity.py` 존재 | 확정 프로필·채용공고 계약으로 연결 |
| 후보 재정렬 | `app/services/reranking.py` | `UNIT_TESTED` | 단위 테스트와 실제 임베딩 기반 테스트 존재 | 분석 실행 API와 결과 계약으로 연결 |

## Python 다음 작업

| 우선순위 | 작업 | 계약 상태 | 시작 조건 |
| ---: | --- | --- | --- |
| 1 | `POST /internal/v1/documents/extract` 라우트 | `contracts/document-extraction.md` MVP 확정 | Python 요청·응답 모델 재확인 |
| 2 | PDF 추출 직후 개인정보 제거 | 정책 확정, 세부 구현 확인 필요 | 제거 필드와 실패 응답 확인 |
| 3 | 이력서 구조화 후보 스키마·프롬프트 | 확인 필요 | 후보 필드·근거 식별자 확정 |
| 4 | 채용공고와 확정 프로필 의미 분석 API | 확인 필요 | Java–Python 분석 계약 작성 |

Python 모델 이름은 현재 연동 검증용 임시값이며 실제 채택 모델은 확인이 필요하다.

## Java 현재 검증 상태

| 기능 | 현재 상태 | 확인 근거 | 다음 단계 |
| --- | --- | --- | --- |
| GitHub OAuth와 고정 사용자 우회 제거 | `UNIT_TESTED` | Java 전체 테스트, 로그인 전 `NONE`, GitHub 로그인 시작 `302` 확인 | 실제 Client ID·Secret으로 브라우저 로그인 |
| 텍스트 문서 등록 | `INTEGRATION_TESTED` | Java·PostgreSQL HTTP 및 Postman 확인 이력 | GitHub 로그인 세션 적용 후 재검증 |
| 공개 GitHub 저장소 등록 | `INTEGRATION_TESTED` | 실제 GitHub 주소 HTTP·Postman 확인 이력 | GitHub 로그인 세션·CSRF 적용 후 재검증 |

인증 변경은 아직 `java` 작업 트리에 커밋되지 않았다. 다른 담당자는 전달 커밋이 생기기 전
`backend-java` 인증 파일과 공통 인증 문서를 수정하지 않는다.

## 통합 차단 요소

- 실제 GitHub OAuth App의 Client ID·Secret 발급과 로컬 callback 등록
- Java의 Python 내부 토큰 헤더 전송 구현
- 문서 추출 API 라우트 구현
- Java–Python 공통 예제 JSON 계약 테스트
- PDF 업로드부터 분석 결과까지 연결된 브라우저 흐름

## 상태 변경 기록

| 날짜 | 영역 | 이전 상태 | 새 상태 | 근거 |
| --- | --- | --- | --- | --- |
| 2026-07-29 | Python 내부 분석 기능 | 구현 완료로 통칭 | `UNIT_TESTED` | 원격 Python 브랜치의 코드·테스트·README 확인 |
| 2026-07-29 | Java GitHub OAuth | 구현 완료로 통칭 | `UNIT_TESTED` | 자동 테스트와 로컬 HTTP 확인, 실제 GitHub 브라우저 로그인 미실행 |
