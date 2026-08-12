# 개발자 채용공고 분석 사용자 API

상태: MVP 계약 확정 — 2026-08-03 사용자 승인, 구현 진행 중

## 범위

사용자가 선택한 공개 GitHub 저장소와 직접 입력한 기술·희망 직무를 근거로,
서버가 허용한 공식 채용 API에서 공고를 찾고 비교한다.

포함:

- 희망 개발 직무와 사용자가 직접 입력한 기술
- 사용자가 등록·선택한 공개 GitHub 저장소
- 서버가 허용한 공식 채용 API 검색
- 조건 판정과 의미 유사도 결과의 분리
- 분석 진행, 부분 완료, 실패와 취소
- 모든 주요 결과와 근거의 연결

제외:

- PDF·이력서·포트폴리오 분석
- 사용자의 채용공고 URL·원문 입력
- Python 또는 LLM의 임의 웹 접속·크롤링
- 합격 확률, 지원자 순위와 불투명한 단일 종합점수

## 책임

| 영역 | 책임 |
|---|---|
| Java | 인증·소유권, 프로필·저장소 선택, 공식 채용 API 호출, 작업 상태·결과 저장, 명확한 조건 판정, 취소 |
| Python | 저장소 근거 추출, 검색 기준 후보 생성, 공고 구조화, 임베딩, 의미 유사도와 재정렬 |
| 사용자 | 희망 직무·기술 입력, 공개 저장소 선택, 결과 근거 확인 |

Java–Python 검색은 [채용공고 검색 도구 계약](../../contracts/job-search-tool.md)을 사용한다.

## 공통 규칙

- 사용자 API는 인증된 서버 세션과 현재 CSRF 정책을 적용한다.
- 응답은 기존 `ApiResponse<T>` 봉투를 사용한다.
- 식별자는 UUID, 시각은 UTC ISO-8601 문자열을 사용한다.
- 사용자는 자신의 리소스만 조회·변경할 수 있다.
- 다른 사용자의 식별자는 존재 여부를 노출하지 않고 `404`로 응답한다.
- 제한값, 타임아웃과 보관 기간은 설정으로 관리한다.
- API 키, 내부 토큰, 모델 자격증명을 응답이나 일반 로그에 포함하지 않는다.

## API 목록

| 기능 | Method | Endpoint |
|---|---|---|
| 분석 프로필 저장 | `PUT` | `/api/v1/user-profile` |
| 분석 프로필 조회 | `GET` | `/api/v1/user-profile` |
| 프로젝트 출처 목록 | `GET` | `/api/v1/project-sources` |
| 분석 시작 | `POST` | `/api/v1/job-analyses` |
| 분석 상태 | `GET` | `/api/v1/job-analyses/{jobAnalysisId}` |
| 분석 이벤트 | `GET` | `/api/v1/job-analyses/{jobAnalysisId}/events` |
| 분석 취소 | `POST` | `/api/v1/job-analyses/{jobAnalysisId}/cancellations` |
| 분석 결과 | `GET` | `/api/v1/job-analyses/{jobAnalysisId}/result` |

기존 공개 GitHub 저장소 등록 API
`POST /api/v1/project-sources/github`는 유지한다.

## 사용자 분석 프로필

```http
PUT /api/v1/user-profile
Content-Type: application/json
```

```json
{
  "expectedVersion": 2,
  "targetJobTitle": "백엔드 개발자",
  "technologyTags": [
    {
      "technologyTagId": "70000000-0000-0000-0000-000000000001",
      "customName": null
    },
    {
      "technologyTagId": null,
      "customName": "LangChain"
    }
  ]
}
```

각 기술 항목은 `technologyTagId`와 `customName` 중 정확히 하나만 사용한다.
표준 태그 선택은 `USER_SELECTED`, 직접 입력은 `USER_CUSTOM`으로 저장하고
GitHub에서 확인된 기술과 구분한다. 커스텀 이름이 표준 태그 또는 별칭과 일치하면
표준 태그 식별자를 연결하되 입력 출처와 원문을 보존한다.
원문 표현은 보존하며 정규화 값으로 덮어쓰지 않는다. 내용이 변경되면 새 프로필
버전을 만들고 기존 분석이 참조한 버전을 변경하지 않는다. 같은 내용을 다시 저장하면
현재 버전을 반환한다. 최초 저장에는 `expectedVersion`을 생략하고 버전 1을 만든다.
기존 내용과 다른 저장 요청은 `expectedVersion`이 현재 버전과 같아야 한다.
같지 않거나 생략하면 `409 USER_PROFILE_VERSION_CONFLICT`를 반환한다.

성공: `200 OK`

```json
{
  "requestId": "8ded49fd-dceb-4599-aac8-7f95e964c197",
  "data": {
    "userProfileId": "ecbca375-2ba2-407a-9e87-29022d2f031a",
    "version": 3,
    "targetJobTitle": "백엔드 개발자",
    "technologyTags": [
      {
        "technologyTagId": "70000000-0000-0000-0000-000000000011",
        "rawName": "Spring Boot",
        "normalizedName": "springboot",
        "displayName": "Spring Boot",
        "sourceType": "USER_SELECTED"
      },
      {
        "technologyTagId": null,
        "rawName": "LangChain",
        "normalizedName": "langchain",
        "displayName": "LangChain",
        "sourceType": "USER_CUSTOM"
      }
    ],
    "updatedAt": "2026-07-31T02:30:00Z"
  },
  "error": null,
  "timestamp": "2026-07-31T02:30:00Z"
}
```

검증 제한은 다음 설정에서 관리한다.

- `user.profile.max-target-job-title-length`
- `user.profile.max-technology-tag-count`
- `user.profile.max-custom-tag-name-length`

조회는 `GET /api/v1/user-profile`을 사용하고 프로필이 없으면 `404`로 응답한다.
표준 태그의 표시명이 변경되어도 기존 프로필 버전에는 당시 표시명을 유지한다.

## 분석 시작

```http
POST /api/v1/job-analyses
Content-Type: application/json
```

```json
{
  "userProfileId": "ecbca375-2ba2-407a-9e87-29022d2f031a",
  "userProfileVersion": 3,
  "projectSourceIds": [
    "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b"
  ]
}
```

Java 검증:

1. 프로필과 프로젝트 출처가 현재 사용자 소유인지 확인한다.
2. 요청한 프로필 버전이 존재하는지 확인한다.
3. 저장소가 공개 상태이며 마지막 검증 결과를 사용할 수 있는지 확인한다.
4. 희망 직무와 최소 입력 조건을 확인한다.
5. 저장소 선택 개수와 동시 작업 제한을 확인한다.

성공: `202 Accepted`

```http
Location: /api/v1/job-analyses/{jobAnalysisId}
```

## 분석 상태

작업 상태:

- `QUEUED`
- `RUNNING`
- `CANCELLATION_REQUESTED`
- `PARTIALLY_COMPLETED`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

진행 단계:

- `VALIDATING_INPUTS`
- `ANALYZING_REPOSITORIES`
- `GENERATING_SEARCH_PLAN`
- `SEARCHING_JOB_POSTINGS`
- `EXTRACTING_JOB_POSTINGS`
- `COMPARING_EVIDENCE`
- `FINALIZING_RESULT`
- `FINISHED`

진행률은 모델의 임의 백분율이 아니라 Java가 저장한
`completedUnits`와 `totalUnits`를 사용한다.

## 분석 이벤트

```http
GET /api/v1/job-analyses/{jobAnalysisId}/events
Accept: text/event-stream
Last-Event-ID: {optional-event-id}
```

이벤트:

- `analysis.status-changed`
- `analysis.step-started`
- `analysis.step-completed`
- `analysis.partial-result`
- `analysis.failed`
- `analysis.finished`

이벤트는 메모리에만 보관하지 않는다. 재연결 시 `Last-Event-ID` 이후 저장된 이벤트를
재전송한다. SSE가 끊겨도 작업은 계속되며 상태 조회 API로 복구한다.

## 분석 취소

```http
POST /api/v1/job-analyses/{jobAnalysisId}/cancellations
```

성공은 `202 Accepted`다. Java는 `CANCELLATION_REQUESTED`를 저장한다.
실행 중 외부 호출이 끝나면 다음 단계로 진행하지 않고 `CANCELLED`로 전환한다.
이미 종료된 작업에는 `409 Conflict`를 반환한다. 확정된 부분 결과는 보존한다.

## 분석 결과

```http
GET /api/v1/job-analyses/{jobAnalysisId}/result
```

`COMPLETED` 또는 `PARTIALLY_COMPLETED`에서만 결과를 반환한다. 실행 중이면
`409`, 결과가 없는 실패·취소 상태이면 `404`다.

결과는 다음 두 영역을 합치지 않는다.

- `conditionResult`: Java가 판정한 필수·우대 조건별 `MATCHED`, `MISMATCHED`,
  `NEEDS_REVIEW`, `NOT_APPLICABLE`, 근거 식별자와 일치율
- `similarityResult`: Python이 계산한 기술·업무·프로젝트 유사도, 모델·버전,
  근거 식별자, 일치 이유와 확인할 수 없는 근거

일치율은 비교 가능한 항목만 분모에 포함한다. `NEEDS_REVIEW`와
`NOT_APPLICABLE`은 제외한다. 유사도는 합격 확률이나 실제 능력 보장이 아니다.

상세 응답 필드, 상태별 HTTP 응답과 프론트 표시 규칙은
[채용공고 분석 결과 API](job-analysis-result-api.md) 제안을 따른다.

## 저장 정책

저장:

- 프로필 버전과 사용자 입력 원문
- 선택한 프로젝트 출처 식별자
- 분석 상태, 단계, 이벤트와 취소 요청 시각
- 검색 기준, 제공자, 공고 식별자, URL, 수집 시각과 공고 버전
- 판정에 필요한 최소 공고 원문 근거와 구조화 값
- 조건 판정, 유사도, 모델·버전과 근거 식별자

저장하지 않음:

- 공식 채용 API 키, 내부 서비스 토큰과 모델 자격증명
- 분석에 불필요한 개인정보
- GitHub 비공개 저장소 내용과 사용자 토큰
- LLM 내부 사고 과정

## 부분 완료와 실패

- 하나 이상의 비교 결과 뒤 일부 단계가 실패하면 `PARTIALLY_COMPLETED`로 저장한다.
- 공식 제공자의 0건 응답은 실패가 아니라 빈 결과의 `COMPLETED`다.
- 하나 이상의 공고 추출에 성공하면 현재 단계를 `COMPARING_EVIDENCE`로 전환한다.
- 공고 추출에는 성공했지만 비교 결과를 만들지 못하면 전체 상태는 `FAILED`로 유지하고,
  `failureCode=COMPARISON_STAGE_NOT_IMPLEMENTED`로 추출 성공과 비교 미완료를 구분한다.
  클라이언트는 이를 일반 추출 실패로 표시하지 않는다.
- 결과를 만들지 못한 검증 실패·장애 요청은 무료 이용량에서 차감하지 않는다.
- MVP에서는 자동 재시도를 구현하지 않는다.

## 주요 오류

| HTTP | code |
|---|---|
| 400 | `INVALID_JOB_ANALYSIS_REQUEST` |
| 401 | `UNAUTHORIZED` |
| 404 | `USER_PROFILE_NOT_FOUND` |
| 404 | `PROJECT_SOURCE_NOT_FOUND` |
| 404 | `JOB_ANALYSIS_NOT_FOUND` |
| 409 | `USER_PROFILE_VERSION_CONFLICT` |
| 409 | `JOB_ANALYSIS_STATE_CONFLICT` |
| 422 | `INSUFFICIENT_ANALYSIS_INPUT` |
| 429 | `JOB_ANALYSIS_LIMIT_EXCEEDED` |
| 502 | `ANALYSIS_DEPENDENCY_INVALID_RESPONSE` |
| 503 | `ANALYSIS_DEPENDENCY_UNAVAILABLE` |

## 구현 순서

1. 이 문서와 내부 검색 도구 계약을 확정한다.
2. 프로필 저장·조회와 프로젝트 출처 목록 API를 구현한다.
3. 분석 작업과 이벤트의 Flyway 스키마를 추가한다.
4. 분석 시작·상태 조회·취소 API를 구현한다.
5. Java–Python 검색 도구와 분석 실행을 연결한다.
6. 조건 판정과 유사도 결과 저장·조회 API를 구현한다.
7. PostgreSQL, 두 서버, Postman과 브라우저 순서로 검증한다.

## 구현 전 확정할 설정

- 희망 직무·기술명의 최대 길이
- 수기 기술·선택 저장소 최대 개수
- 사용자별 동시 분석 최대 개수
- 분석별 검색 도구 호출 최대 횟수와 공고 최대 수
- 분석 작업·이벤트·공고 근거·결과의 보관 기간
- SSE 연결 유지 시간과 heartbeat 주기

## 공식 참고 자료

- [Spring MVC annotated controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring MVC asynchronous requests and SSE](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [인사혁신처_공공취업정보 조회 서비스](https://www.data.go.kr/data/15156780/openapi.do)
