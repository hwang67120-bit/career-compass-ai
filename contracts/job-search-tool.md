# 채용공고 검색 도구 계약

상태: 제안 — Java와 Python 구현 전 공동 확정 필요

## 목적

Python은 임의 인터넷 접속 권한을 갖지 않는다. 분석 중 공고 검색이 필요하면 Java가
제공하는 이 내부 API만 호출한다. Java는 허용된 공식 채용 API의 주소·자격증명을
소유하고 요청 검증, 호출 제한, 출처 기록과 응답 정규화를 담당한다.

MVP 허용 제공자:

1. 사람인 공식 채용정보 API
2. 고용24 Open API

제공자 주소와 API 키는 Java 설정에서 관리한다. Python 요청에는 URL, 제공자 코드,
API 키, 페이지 번호와 자유 형식 검색식을 포함하지 않는다.

## 요청

```http
POST /internal/v1/tools/job-search
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

```json
{
  "jobAnalysisId": "9b12bb6a-8c88-441e-8395-462344107726",
  "toolCallId": "e61357d9-8284-42c6-b234-f86576ccfe6c",
  "targetJobTitle": "백엔드 개발자",
  "skillKeywords": ["Java", "Spring Boot", "PostgreSQL"]
}
```

| 필드 | 타입 | 의미 |
|---|---|---|
| `jobAnalysisId` | UUID | Java가 생성한 실행 중 분석 작업 |
| `toolCallId` | UUID | 분석 안에서 호출을 식별하는 멱등 키 |
| `targetJobTitle` | string | 사용자 프로필에 근거한 희망 개발 직무 |
| `skillKeywords` | string[] | 사용자 입력 또는 저장소 근거로 확인된 기술 |

Java 검증:

1. 분석 작업이 실제로 존재하며 `RUNNING`인지 확인한다.
2. 희망 직무와 기술이 고정된 프로필·저장소 근거 범위 안인지 확인한다.
3. 키워드 개수·길이, 도구 호출 횟수와 결과 수 제한을 적용한다.
4. 같은 `toolCallId`가 성공했다면 저장된 동일 응답을 반환한다.
5. 같은 `toolCallId`에 다른 요청 내용이면 `409`를 반환한다.
6. 취소 요청 상태이면 외부 API를 호출하지 않는다.

## 제공자 실행 규칙

MVP에서는 측정되지 않은 병렬 호출과 자동 재시도를 사용하지 않는다.

1. 사람인 API를 먼저 호출한다.
2. 사람인이 정상 응답하고 결과가 있으면 사용한다.
3. 사람인이 정상 0건이거나 사용 불가이면 고용24를 fallback으로 호출한다.
4. 두 제공자 모두 정상 0건이면 성공한 빈 목록을 반환한다.
5. 두 제공자 모두 사용 불가이면 `503`을 반환한다.

## 성공 응답

성공: `200 OK`

```json
{
  "requestId": "674e5357-dc0b-42a5-92dc-e12fc2df2292",
  "data": {
    "jobAnalysisId": "9b12bb6a-8c88-441e-8395-462344107726",
    "toolCallId": "e61357d9-8284-42c6-b234-f86576ccfe6c",
    "status": "COMPLETED",
    "provider": "SARAMIN",
    "criteria": {
      "targetJobTitle": "백엔드 개발자",
      "skillKeywords": ["Java", "Spring Boot", "PostgreSQL"]
    },
    "jobPostings": [
      {
        "providerPostingId": "provider-posting-123",
        "sourceUrl": "https://example.invalid/job/123",
        "companyName": "예시회사",
        "originalJobTitle": "Java 백엔드 개발자",
        "publishedDate": "2026-07-25",
        "closingDate": "2026-08-10",
        "employmentType": "정규직",
        "locationText": "서울",
        "sourceText": "공식 API가 제공한 분석 대상 원문",
        "collectedAt": "2026-07-31T02:41:00Z"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-07-31T02:41:01Z"
}
```

`status`는 첫 제공자 결과인 `COMPLETED` 또는 fallback 결과인
`FALLBACK_COMPLETED`다. `provider`는 실제 결과를 제공한 `SARAMIN` 또는
`WORK24`다.

## 정규화와 중복 후보

Java는 공식 응답을 정규화하되 원문 사실을 AI 결과로 덮어쓰지 않는다.
중복 후보는 다음 순서로 표시한다.

1. 같은 제공자의 `providerPostingId`
2. 정규화한 `sourceUrl`
3. 회사명, 원문 직무명과 게시일 조합

3번은 동일 공고 확정이 아니라 중복 후보이므로 출처 이력을 보존한다.

## 오류

| HTTP | code | retryable |
|---|---|---|
| 401 | `INTERNAL_UNAUTHORIZED` | false |
| 404 | `JOB_ANALYSIS_NOT_FOUND` | false |
| 409 | `JOB_ANALYSIS_NOT_RUNNING` | false |
| 409 | `TOOL_CALL_CONFLICT` | false |
| 422 | `INVALID_JOB_SEARCH_CRITERIA` | false |
| 429 | `JOB_SEARCH_TOOL_LIMIT_EXCEEDED` | false |
| 502 | `JOB_PROVIDER_INVALID_RESPONSE` | true |
| 503 | `JOB_PROVIDERS_UNAVAILABLE` | true |

## 저장과 로그

Java 저장 대상:

- `jobAnalysisId`, `toolCallId`, 요청 기준 해시
- 사용한 제공자와 fallback 여부
- 외부 요청 시작·종료 시각, HTTP 상태 범주와 결과 개수
- 정규화한 공고, 출처 URL, 수집 시각과 원문 버전
- 계약 오류 유형과 retryable 여부

Java는 API 키, 내부 토큰, 전체 외부 응답과 불필요한 개인정보를 일반 로그에
남기지 않는다. Python은 받은 공고를 임의 URL로 다시 조회하지 않는다.

## 공동 계약 테스트

1. 정상 결과와 정상 0건
2. fallback 성공
3. 잘못된 UUID와 검색 기준
4. 실행 중이 아닌 작업
5. 같은 `toolCallId`의 동일 요청과 충돌 요청
6. 호출 제한 초과
7. 제공자 응답 계약 오류와 모든 제공자 사용 불가
8. 취소 요청 이후 호출 차단

## 공식 참고 자료

- [사람인 채용정보 API 안내](https://oapi.saramin.co.kr/guide/info)
- [고용24 Open API 소개](https://www.work24.go.kr/cm/e/a/0110/selectOpenApiIntro.do)
