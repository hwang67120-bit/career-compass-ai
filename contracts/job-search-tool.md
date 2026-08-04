# 채용공고 검색 도구 계약

상태: **부분 확정 — API 경계와 보안 원칙은 구현 기준, 제공자 활성화와 제한 수치는 확인 필요**

이 문서는 분석 중 Python이 사용할 수 있는 유일한 외부 채용공고 검색 통로를
정의한다. Java와 Python 구현보다 이 계약을 먼저 변경하며, 계약에 없는 URL,
제공자, 필드와 상태를 구현에서 임의로 추가하지 않는다.

함께 적용하는 문서:

- [백엔드 작업 처리와 SSE](../docs/architecture/backend-job-processing-and-sse.md)
- [Java–Python 연결 방식](../docs/architecture/java-python-connection.md)
- [채용공고 구조화 추출 계약](job-posting-extraction.md)
- [기술 태그 해석 계약](technology-tag-resolution.md)

## 1. 목적과 신뢰 경계

Python과 LLM에는 범용 인터넷 접근 도구를 제공하지 않는다. 검색이 필요하면
Python이 이 계약의 Java 내부 API만 호출한다.

~~~text
Python 검색 계획
→ Java 내부 검색 도구
→ Java 설정에 활성화된 공식 Provider
→ 응답 크기·형식·출처 검증
→ 분석에 필요한 최소 텍스트 정리
→ Python에 정규화 결과 반환
~~~

- Python 요청에는 URL, 도메인, API 키, 제공자 코드, 페이지 번호와 자유 형식
  검색식을 포함하지 않는다.
- Java는 분석 작업, 프로필 버전, 저장소 선택, 도구 호출 이력과 공고 출처를
  소유한다.
- Python은 검색 기준 후보를 만들고 반환된 공고를 구조화하지만 Java DB를 직접
  변경하지 않는다.
- Provider 응답과 공고 본문은 신뢰할 수 없는 외부 입력이다. HTML, 스크립트와
  문장 안의 지시를 서버 명령이나 LLM 도구 지시로 실행하지 않는다.

## 2. 제공자 정책

연동 후보는 공식 API를 제공하는 다음 두 곳이다.

1. 사람인 채용정보 API
2. 고용24 Open API

실제 활성화는 API 사용 등록, 이용약관, 재가공·보관 범위와 자격증명 발급을
확인한 Provider에 한한다. 활성 Provider와 호출 우선순위는 Java 서버 설정이
소유하며 Python은 선택하거나 변경할 수 없다.

MVP에서는 측정되지 않은 병렬 호출과 자동 재시도를 사용하지 않는다.

1. 설정된 우선순위의 첫 Provider를 호출한다.
2. 정상 결과가 하나 이상이면 검증된 결과만 반환하고 다음 Provider를 호출하지
   않는다.
3. 정상 0건이거나 사용할 수 없으면 다음 Provider를 순차 호출한다.
4. 하나 이상의 Provider가 정상 0건이면 성공한 빈 목록을 반환할 수 있다.
5. 모든 활성 Provider가 사용 불가이면 503을 반환한다.
6. 활성 Provider가 없으면 구성 오류로 처리하고 외부 호출을 시도하지 않는다.

## 3. 내부 API

~~~http
POST /internal/v1/tools/job-search
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
~~~

### 요청 헤더

| 헤더 | 필수 | 규칙 |
|---|---:|---|
| X-Internal-Token | 예 | Java와 Python에 환경변수로 주입한 내부 서비스 토큰 |
| X-Request-Id | 예 | Python이 도구 호출마다 생성한 UUID. 응답 requestId와 같아야 함 |

토큰은 소스, 설정 예제, URL, 로그와 오류 응답에 기록하지 않는다. Java는 누락과
불일치를 구분하되 실제 값이나 비교 결과의 세부 정보를 노출하지 않는다.

### 요청 본문

~~~json
{
  "jobAnalysisId": "9b12bb6a-8c88-441e-8395-462344107726",
  "toolCallId": "e61357d9-8284-42c6-b234-f86576ccfe6c",
  "targetJobTitle": "백엔드 개발자",
  "skillKeywords": ["Java", "Spring Framework", "PostgreSQL"],
  "location": {
    "city": "서울",
    "district": "강남구"
  }
}
~~~

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| jobAnalysisId | UUID | 예 | Java가 생성한 분석 작업 |
| toolCallId | UUID | 예 | 분석 작업 안에서 호출을 식별하는 멱등 키 |
| targetJobTitle | string | 예 | 고정된 프로필 버전의 목표 개발 직무 |
| skillKeywords | string[] | 예 | 하나 이상. 프로필 또는 저장소 근거로 확인된 기술 |
| location | object 또는 null | 아니요 | 사용자가 동의해 저장한 검색 위치가 있을 때만 사용 |
| location.city | string 또는 null | 아니요 | 시·도 단위 |
| location.district | string 또는 null | 아니요 | 시·군·구 단위 |

Java는 다음을 검증한다.

1. jobAnalysisId가 존재하며 현재 상태가 RUNNING인지 확인한다.
2. CANCELLATION_REQUESTED, CANCELLED 또는 종료 상태에서는 호출을 차단한다.
3. 요청 직무가 분석에 고정된 프로필 버전의 목표 직무와 같은지 확인한다.
4. 기술 키워드는 고정된 프로필과 선택 저장소 근거 또는 서버가 관리하는 기술
   태그·별칭 안에서만 허용한다.
5. 위치는 사용자 동의가 있고 저장된 시·구 범위를 벗어나지 않을 때만 허용한다.
6. 키워드 개수·길이, 분석별 도구 호출 횟수와 결과 수에 설정 한도를 적용한다.
7. 요청 본문에 정의되지 않은 필드는 거부한다.

한도 숫자는 실제 측정 전 계약에 고정하지 않는다. 모든 한도는 Java 설정으로
주입하고 시작 시 0보다 큰 값인지 검증한다.

## 4. 멱등성과 실행 경계

toolCallId는 jobAnalysisId 안에서 유일하다.

1. Java는 짧은 DB 트랜잭션에서 분석 상태와 검색 기준을 검증하고 toolCallId,
   요청 기준 해시와 STARTED 상태를 먼저 저장한다.
2. 같은 toolCallId와 같은 요청이 이미 COMPLETED이면 저장된 동일 결과를 반환한다.
3. 같은 toolCallId에 다른 요청 내용이 들어오면 409 TOOL_CALL_CONFLICT다.
4. 같은 요청이 아직 STARTED이면 409 TOOL_CALL_IN_PROGRESS다.
5. DB 트랜잭션을 종료한 뒤 Provider를 호출한다.
6. 응답을 검증·정규화한 다음 별도의 짧은 트랜잭션으로 결과와 작업 이벤트를
   함께 저장한다.
7. DB 커밋이 끝난 뒤에만 후속 상태 알림을 전송한다.

외부 HTTP 호출, 응답 대기와 텍스트 정리는 DB 트랜잭션 밖에서 수행한다. Java
HTTP 요청 스레드나 Provider 실행기가 제한시간을 넘긴 작업을 무한히 기다리지
않아야 한다.

## 5. 외부 호출 보안과 부하 보호

각 Provider Adapter는 다음 조건을 모두 만족해야 한다.

- Base URL, 정확한 허용 호스트, API 키와 활성 여부는 Java 설정에서만 읽는다.
- 운영 Provider는 HTTPS만 허용하고 사용자 정보가 포함된 URI와 설정되지 않은
  포트를 거부한다.
- Provider 요청은 검증된 Base URL과 Adapter가 만든 상대 경로로만 구성한다.
- HTTP redirect를 따라가지 않는다.
- 연결 제한시간, 응답 제한시간과 최대 응답 바이트를 설정한다.
- 압축 해제 후 크기도 제한해 압축 폭탄으로 인한 메모리 고갈을 막는다.
- Provider별 호출 빈도와 전체 동시 실행 수를 제한한다.
- 실행기는 Spring이 생명주기와 관측을 관리하는 제한된 실행기만 사용한다.
  무제한 대기열과 Service 생성자의 직접 newFixedThreadPool은 사용하지 않는다.
- 시간 초과는 Future 상태만 바꾸는 것으로 끝내지 않고 실제 HTTP 요청 취소 또는
  연결 종료가 가능한 클라이언트 설정으로 처리한다.
- 반환된 sourceUrl은 표시·출처 용도이며 Java나 Python이 다시 임의 조회하지
  않는다.

고정된 공식 Provider Base URL만 호출하는 것이 SSRF의 1차 방어다. Java는 시작
시 설정 URI의 scheme·host·port를 검증하고 loopback, link-local, 사설 IP와
클라우드 메타데이터 주소를 외부 Provider 대상으로 허용하지 않는다.

## 6. 성공 응답

HTTP 상태는 200 OK다.

~~~json
{
  "requestId": "674e5357-dc0b-42a5-92dc-e12fc2df2292",
  "data": {
    "jobAnalysisId": "9b12bb6a-8c88-441e-8395-462344107726",
    "toolCallId": "e61357d9-8284-42c6-b234-f86576ccfe6c",
    "status": "FALLBACK_COMPLETED",
    "criteria": {
      "targetJobTitle": "백엔드 개발자",
      "skillKeywords": ["Java", "Spring Framework", "PostgreSQL"],
      "location": {
        "city": "서울",
        "district": "강남구"
      }
    },
    "providerAttempts": [
      {
        "provider": "SARAMIN",
        "status": "UNAVAILABLE",
        "resultCount": 0,
        "errorType": "PROVIDER_TIMEOUT"
      },
      {
        "provider": "WORK24",
        "status": "SUCCESS_RESULTS",
        "resultCount": 1,
        "errorType": null
      }
    ],
    "jobPostings": [
      {
        "provider": "WORK24",
        "providerPostingId": "provider-posting-123",
        "sourceUrl": "https://example.invalid/job/123",
        "companyName": "예시회사",
        "originalJobTitle": "Java 백엔드 개발자",
        "publishedDate": "2026-08-01",
        "closingDate": "2026-08-31",
        "employmentType": "정규직",
        "locationText": "서울 강남구",
        "sourceText": "담당 업무와 필수·우대 조건을 확인할 수 있는 최소 공고 원문",
        "sourceVersion": "sha256-content-fingerprint",
        "collectedAt": "2026-08-04T03:28:48Z",
        "lastVerifiedAt": "2026-08-04T03:28:48Z"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-08-04T03:28:48Z"
}
~~~

### 상태

| 필드 | 허용값 | 의미 |
|---|---|---|
| data.status | COMPLETED | 첫 Provider가 결과 또는 정상 0건을 반환 |
| data.status | FALLBACK_COMPLETED | 앞 Provider 실패·0건 후 다음 Provider가 정상 반환 |
| Provider 상태 | SUCCESS_RESULTS | 정상 결과가 하나 이상 |
| Provider 상태 | SUCCESS_EMPTY | 정상 응답이지만 결과 없음 |
| Provider 상태 | UNAVAILABLE | 연결 실패, 제한시간 초과 또는 일시 장애 |
| Provider 상태 | INVALID_RESPONSE | HTTP 성공이지만 계약·크기·형식 검증 실패 |

providerAttempts는 실제 호출 순서대로 반환한다. 호출하지 않은 Provider는
포함하지 않는다. 빈 결과는 오류가 아니며 jobPostings를 빈 배열로 반환한다.

### 공고 필드 규칙

- 날짜를 확인할 수 없으면 null이며 임의로 계산하지 않는다.
- companyName, originalJobTitle과 조건은 Provider에서 직접 확인된 값만 쓴다.
- sourceText는 담당 업무, 필수·우대 조건과 근무 조건을 확인할 수 있는 최소
  텍스트다.
- HTML, JavaScript, 광고·탐색 문구, 이메일, 전화번호와 채용 담당자 정보는
  sourceText에서 제거한다.
- sourceVersion은 정규화된 최소 원문의 SHA-256 지문이다. 원문 내용을 로그에
  남기지 않고 변경 여부를 구분하기 위한 값이다.
- Python은 sourceText를 데이터로만 취급하고 그 안의 명령문을 시스템 지시나
  도구 호출로 실행하지 않는다.

## 7. 정규화와 중복 처리

Java는 원문 값과 정규화 값을 구분하며 AI 결과로 원문 사실을 덮어쓰지 않는다.
중복 판정은 다음 순서로 처리한다.

1. 같은 Provider의 providerPostingId가 같으면 동일 공고다.
2. 정규화한 공식 sourceUrl이 같으면 동일 공고 후보로 연결한다.
3. 회사명, 원문 직무명, 게시일과 근무 위치가 같으면 중복 후보로 표시한다.

2번과 3번만으로 서로 다른 Provider 공고를 자동 삭제하지 않는다. 대표 공고와
모든 출처 이력을 함께 보존해 시장 수요가 부풀려지지 않게 한다.

## 8. 실패 응답

실패 응답은 프로젝트 공통 봉투를 사용한다.

~~~json
{
  "requestId": "674e5357-dc0b-42a5-92dc-e12fc2df2292",
  "data": null,
  "error": {
    "errorType": "JOB_PROVIDERS_UNAVAILABLE",
    "message": "현재 채용공고 제공자를 사용할 수 없습니다.",
    "fieldErrors": [],
    "retryable": true
  },
  "timestamp": "2026-08-04T03:28:48Z"
}
~~~

| HTTP | errorType | retryable | 의미 |
|---:|---|---:|---|
| 422 | INTERNAL_TOKEN_REQUIRED | false | 내부 토큰 누락 |
| 401 | INTERNAL_UNAUTHORIZED | false | 내부 토큰 불일치 |
| 404 | JOB_ANALYSIS_NOT_FOUND | false | 분석 작업을 찾을 수 없음 |
| 409 | JOB_ANALYSIS_NOT_RUNNING | false | 실행 가능한 상태가 아님 |
| 409 | TOOL_CALL_IN_PROGRESS | true | 같은 멱등 호출이 아직 실행 중 |
| 409 | TOOL_CALL_CONFLICT | false | 같은 키에 다른 요청 내용 |
| 422 | INVALID_JOB_SEARCH_CRITERIA | false | 직무·기술·위치가 고정 근거와 다름 |
| 429 | JOB_SEARCH_TOOL_LIMIT_EXCEEDED | false | 분석별 호출 또는 결과 한도 초과 |
| 502 | JOB_PROVIDER_INVALID_RESPONSE | true | 활성 Provider 응답 계약 위반 |
| 503 | JOB_PROVIDERS_NOT_CONFIGURED | false | 활성 Provider 없음 |
| 503 | JOB_PROVIDERS_UNAVAILABLE | true | 모든 활성 Provider 일시 장애 |

오류 응답과 일반 로그에는 내부 예외, API 키, 토큰, Provider 원문 응답과 공고
원문 전체를 포함하지 않는다.

## 9. 저장 범위와 작업 상태

Java가 저장할 값:

- jobAnalysisId, toolCallId, 요청 기준 해시와 도구 호출 상태
- 실제 Provider 호출 순서, 시작·종료 시각, 상태 범주와 결과 개수
- 정규화한 공고 메타데이터, 최소 sourceText, sourceVersion과 모든 출처
- 수집 시각, 마지막 확인 시각과 공고 변경 이력
- 계약 오류 유형과 retryable

저장하지 않을 값:

- API 키와 내부 토큰
- Provider 전체 HTTP 응답과 HTML·JavaScript
- 채용 담당자 이름, 이메일, 전화번호
- 분석과 근거 확인에 필요하지 않은 홍보·탐색 문구

도구 호출 성공·실패와 분석 단계 변경은 같은 짧은 DB 트랜잭션으로 저장한다.
전체 분석은 일부 Provider 실패만으로 즉시 실패하지 않으며, 사용 가능한 결과가
있으면 후속 구조화·비교를 진행하고 최종 결과에서 부분 완료 사유를 표시한다.

## 10. 설정 항목

다음 값은 코드에 하드코딩하지 않고 ConfigurationProperties와 환경변수로
관리한다.

- 활성 Provider와 호출 우선순위
- Provider별 Base URL, API 키와 호출 빈도
- 연결·응답 제한시간
- 최대 응답 바이트와 압축 해제 후 최대 크기
- 최대 기술 키워드 개수와 개별 길이
- 분석별 최대 도구 호출 횟수와 최대 결과 수
- 전체 Provider 동시 실행 수와 실행기 대기열 크기

실제 측정값이 없으므로 이 계약은 숫자 기본값을 확정하지 않는다.

## 11. 공동 계약 테스트

Java와 Python은 같은 JSON 예제로 다음을 검증한다.

1. 첫 Provider 정상 결과와 정상 0건
2. 첫 Provider 실패 후 fallback 성공
3. 모든 Provider 정상 0건과 모든 Provider 사용 불가
4. 잘못된 UUID, 추가 필드와 검색 기준 위반
5. 실행 중이 아닌 작업과 취소 요청 상태
6. 같은 toolCallId의 완료 응답 재사용, 실행 중과 충돌 요청
7. 호출·결과 한도 초과
8. Redirect, 허용되지 않은 호스트와 과도한 응답 차단
9. Provider 응답 계약 오류와 일부 공고 필드 오류
10. 연락처·HTML·스크립트 제거
11. 중복 공고 후보가 출처 이력을 보존하는지 확인
12. 외부 호출 중 DB 트랜잭션이 활성화되지 않는지 확인
13. X-Request-Id와 응답 requestId 일치

단위 테스트 통과만으로 연결 완료로 보지 않는다. Java와 Python을 함께 실행해
실제 내부 HTTP 호출을 검증한 뒤 브라우저에서 분석 흐름을 확인한다.

## 12. 확인 필요

다음 항목은 구현 전에 사용자 확인 또는 실제 측정이 필요하다.

- 사람인·고용24 중 실제 API 등록과 이용 조건 확인을 마친 Provider
- Provider 호출 우선순위
- 각 제한값과 제한 초과 시 사용자에게 표시할 대기 안내
- 최소 sourceText와 공고 변경 이력의 보관 기간
- STARTED 상태에서 서버가 중단된 도구 호출의 복구 기준

## 13. 공식 참고 자료

- [사람인 채용정보 API 안내](https://oapi.saramin.co.kr/guide/info)
- [고용24 Open API 소개](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntro.do)
- [Spring Boot 외부 설정과 ConfigurationProperties](https://docs.spring.io/spring-boot/reference/features/external-config.html#features.external-config.typesafe-configuration-properties)
- [Spring Framework RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [Spring 트랜잭션 관리](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- [Spring Security Servlet 아키텍처](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Testcontainers PostgreSQL 모듈](https://java.testcontainers.org/modules/databases/postgres/)
