# 현재 작업 상태

이 문서는 Java·Python·공통 계약 작업의 현재 위치와 검증 수준을 공유한다.
`구현 완료`라는 표현 대신 실제로 통과한 가장 높은 검증 상태를 기록한다.

- 마지막 확인일: 2026-07-30
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
| 저장소 코드 근거 추출(결정론적, LLM 없음) | `app/services/repository_evidence.py`, `app/providers/github_repository.py` | `UNIT_TESTED` | 순수 로직 단위 테스트 8건 + 실제 GitHub API 호출 테스트(`octocat/Hello-World`) 4건 통과 | Java가 owner·repository·commitSha를 넘기는 API 라우트로 연결. 매니페스트·키워드·언어 확장자 목록은 확인 필요 |

## Python 다음 작업

2026-07-30 사용자 확인: 비교 범위를 줄였다. 비교 근거는 **이력서 PDF가 아니라 GitHub 저장소 코드와
수기 입력 기술 키워드**를 사용한다 — 이 두 출처는 개인정보를 포함하지 않기 때문이다.
아래 10개가 Python이 구현할 전체 범위다(사용자 확정, 우선순위 순서).

| 우선순위 | 작업 | 계약 상태 | 시작 조건 |
| ---: | --- | --- | --- |
| 1 | GitHub 저장소 코드에서 기술·프로젝트 근거 추출 | 확인 필요 | 저장소 코드 접근 방식(클론 대상 파일 범위, 언어별 분석 방법), 근거 스키마 확정 |
| 2 | 수기 입력 기술과 저장소 근거 분리 | 확인 필요 | 수기 입력 스키마, 두 근거를 구분해서 저장·표시하는 규칙 확정 |
| 3 | 희망 직무 기반 채용공고 검색어 생성 | 확인 필요 | 생성한 검색어의 용도(사용자에게 노출만 하는지, 다른 조회 API에 전달하는지) 확정 |
| 4 | 채용공고 구조화 추출 API 확정 | `contracts/job-posting-extraction.md` 제안 | 계약 MVP 확정 |
| 5 | 사용자 경험·주요 업무 임베딩(저장소+수기 키워드 기준) | 확인 필요 | 1·2번 결과 스키마 확정 |
| 6 | 기술·프로젝트 의미 유사도 계산 | 기존 `app/services/similarity.py` 재사용 가능 | 5번 임베딩 결과 |
| 7 | 적합한 채용공고 재정렬 | 기존 `app/services/reranking.py` 재사용 가능 | 6번 결과 |
| 8 | 부족 기술과 추천 이유 생성 | 확인 필요 | 근거 있는 값만 생성하는 규칙(`AGENTS.md` "사실, 추정과 미확인 구분") 적용 |
| 9 | 근거 없는 기술·경력 제거 | 확인 필요 | 8번과 함께 근거 검증 규칙 확정 |
| 10 | 모델 성능·토큰·단계별 처리시간 측정 | 확인 필요 | 측정할 단계 범위와 기록 위치(로그·별도 저장소) 확정 |

Python 모델 이름은 현재 연동 검증용 임시값이며 실제 채택 모델은 확인이 필요하다.

**확인 필요 — 이력서 PDF 파이프라인 처리**: 기존에 구현한 이력서 PDF 업로드·추출·개인정보 제거
(`contracts/document-extraction.md`, `app/documents/`)가 이 비교 기능에서 완전히 빠지는 것인지,
아니면 다른 용도로 남겨두고 비교 기능만 저장소+수기 키워드로 가는 것인지 아직 확정되지 않았다.
확정 전까지 이 파이프라인의 기존 구현·테스트는 유지하고 삭제하지 않는다.

4번은 아래 "채용공고 외부 조회"에서 채용공고 원문(`sourceText`)을 확보하는 방법과 맞물려 있다.
그 원문 확보 방법(Java 서버사이드 조회)이 끝나기 전까지 Python은 임시 샘플 채용공고 텍스트로
임베딩·유사도·재정렬을 연결해 두고, 모델 정확도(어떤 모델·프롬프트가 근거 있는 결과를 내는지)를
계속 검증한다. 이 임시 연결은 `제안` 상태이며 실제 Java 요청으로 계약 검증을 마치기 전까지
완료로 보지 않는다.

## 채용공고 외부 조회 (제안 — 확인 필요)

2026-07-30 사용자 확인: 채용공고 URL을 AI가 직접 검색하거나 브라우저를 조작해 여러 사이트를 돌아다니며
수집하는 방식은 채택하지 않는다. 사용자가 채용공고 URL을 직접 입력하고, Java가 그 호스트가 허용된
채용 사이트 목록에 속하는지 확인한 뒤 서버에서 직접 조회하는 방식으로 제한한다.
공개 GitHub 저장소 등록(`/api/v1/project-sources/github`)과 같은 구조다.

- 허용 도메인 후보(사용자 제시, 최종 목록·정확한 호스트명은 확인 필요): 잡코리아, 게임잡, 원티드, 알바몬, 취업24
- 제외 대상: 위 목록에 없는 모든 출처. 사용자가 예시로 든 네이버 카페·당근마켓 구인구직 글처럼
  신뢰도를 확인할 수 없는 비공식 채널은 포함하지 않는다.
- Java가 조회한 본문 텍스트는 기존 계약(`contracts/job-posting-extraction.md`)의 `sourceText`로 그대로
  전달하므로 그 계약을 새로 만들 필요는 없다. 다만 Java가 사용자에게 노출하는 자체 API(공고 등록·조회)는
  별도 계약이 필요하다 — 아래 "Java 사용자 API 요구 필드" 참고.

허용 도메인 검사만으로는 SSRF를 막지 못한다. 서버사이드 조회 API는 최소한 다음을 만족해야 한다(2026-07-30 코드 리뷰 반영, 확인 필요):

- 최초 요청뿐 아니라 **리다이렉트 목적지도 같은 허용 목록으로 재검증**한다(허용 도메인이 사설 IP·다른 도메인으로 리다이렉트하는 경우 차단).
- `localhost`, `127.0.0.1`, 사설 IP 대역(`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), 클라우드 메타데이터 주소(`169.254.169.254` 등)로의 접속을 차단한다.
- 허용 도메인의 DNS 응답이 요청 시점과 실제 접속 시점 사이에 바뀌는 경우(DNS 재해석/리바인딩)를 방어한다.
- 응답 크기 상한, MIME 타입 검사(HTML 이외 응답 거부), 연결·읽기 타임아웃을 설정한다.

Java 사용자 API 요구 필드(제안 — 확인 필요): `contracts/job-posting-extraction.md`의 `sourceText`는
Python에 전달하는 값일 뿐이고, Java가 공고를 저장·조회하는 자체 API에는 최소한 아래 필드가 더 필요하다.

- 원본 URL
- 수집 시각, 마지막 확인 시각
- 공고 버전(재조회 시 이전 버전을 덮어쓰지 않음 — `AGENTS.md` "회사와 채용공고" 절 참고)
- 조회 실패 상태(예: 접속 불가, 허용 도메인 아님, 리다이렉트 거부, 크기·형식 초과)

| 우선순위 | 작업 | 계약 상태 | 시작 조건 |
| ---: | --- | --- | --- |
| 1 | 채용공고 URL 입력 → 허용 도메인·SSRF 방어 검증 → 서버사이드 본문 조회 API | 확인 필요 | 허용 도메인 최종 목록 확정, 각 사이트 약관상 서버 조회 허용 여부 확인, SSRF 방어 조건 구현 |
| 2 | Java 공고 등록·조회 API 계약 작성(원본 URL·수집 시각·버전·실패 상태 포함) | 확인 필요 | 위 1번과 별개로 사용자·Codex 확인 |

이 표는 Java(Codex) 담당 영역(`backend-java`)의 다음 작업이며, Python 담당은 직접 구현하지 않고
계약 영향만 공유한다.

## Java 현재 검증 상태

| 기능 | 현재 상태 | 확인 근거 | 다음 단계 |
| --- | --- | --- | --- |
| GitHub OAuth와 고정 사용자 우회 제거 | `UNIT_TESTED` | Java 전체 테스트, 로그인 전 `NONE`, GitHub 로그인 시작 `302` 확인 | 실제 Client ID·Secret으로 브라우저 로그인 |
| Python 문서 추출 내부 클라이언트 | `UNIT_TESTED` | Java 21 전체 테스트 89개 통과, 내부 토큰·multipart·요청 ID·성공·오류 봉투 계약 단위 검증 | Java와 Python을 함께 실행해 실제 HTTP 계약 검증 |
| 텍스트 문서 등록 | `INTEGRATION_TESTED` | Java·PostgreSQL HTTP 및 Postman 확인 이력 | GitHub 로그인 세션 적용 후 재검증 |
| 공개 GitHub 저장소 등록 | `INTEGRATION_TESTED` | 실제 GitHub 주소 HTTP·Postman 확인 이력 | GitHub 로그인 세션·CSRF 적용 후 재검증 |

인증 변경은 아직 `java` 작업 트리에 커밋되지 않았다. 다른 담당자는 전달 커밋이 생기기 전
`backend-java` 인증 파일과 공통 인증 문서를 수정하지 않는다.

## 통합 차단 요소

- 실제 GitHub OAuth App의 Client ID·Secret 발급과 로컬 callback 등록
- Java–Python 공통 예제 JSON 계약 테스트
- PDF 업로드부터 분석 결과까지 연결된 브라우저 흐름

## 상태 변경 기록

| 날짜 | 영역 | 이전 상태 | 새 상태 | 근거 |
| --- | --- | --- | --- | --- |
| 2026-07-29 | Python 내부 분석 기능 | 구현 완료로 통칭 | `UNIT_TESTED` | 원격 Python 브랜치의 코드·테스트·README 확인 |
| 2026-07-29 | Java GitHub OAuth | 구현 완료로 통칭 | `UNIT_TESTED` | 자동 테스트와 로컬 HTTP 확인, 실제 GitHub 브라우저 로그인 미실행 |
| 2026-07-29 | Java Python 문서 추출 클라이언트 | `IMPLEMENTED` | `UNIT_TESTED` | Java 21에서 대상 클라이언트 테스트와 전체 89개 테스트를 캐시 없이 실행해 통과 |
