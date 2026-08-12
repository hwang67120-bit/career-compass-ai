# 채용공고 분석 결과 API

상태: 제안 — 12절의 확인 필요 항목을 사용자 승인하기 전에는 구현하지 않는다.

## 1. 목적과 범위

완료 또는 부분 완료된 채용공고 분석 결과를 현재 사용자에게 반환한다. 결과는 공고별
명확한 기술 조건 판정과 의미 유사도를 분리하며, 합격 확률이나 불투명한 단일 종합점수를
제공하지 않는다.

현재 데이터로 확정 가능한 조건은 공고의 필수·우대 기술과 사용자가 입력한 기술 태그다.
경력·학력·자격증·지역·고용 형태는 사용자 프로필에 해당 상태가 추가되기 전까지 결과
필드를 만들거나 추측하지 않는다.

## 2. 엔드포인트

```http
GET /api/v1/job-analyses/{jobAnalysisId}/result
Accept: application/json
```

- 사용자 인증과 현재 CSRF 정책을 적용한다.
- 사용자는 자신의 분석 결과만 조회할 수 있다.
- 다른 사용자의 분석 식별자는 존재 여부를 노출하지 않고 `404`를 반환한다.
- 결과는 분석이 고정한 사용자 프로필 버전과 프로젝트 출처 버전을 사용한다.

## 3. 조회 가능 상태

| 분석 상태 | HTTP | 동작 |
|---|---:|---|
| `QUEUED`, `RUNNING`, `CANCELLATION_REQUESTED` | 409 | 아직 결과가 확정되지 않음 |
| `COMPLETED` | 200 | 전체 결과 반환 |
| `PARTIALLY_COMPLETED` | 200 | 성공한 결과와 실패한 영역을 함께 반환 |
| `FAILED`, `CANCELLED` | 404 | 사용자에게 제공할 비교 결과가 없음 |

공식 Provider가 정상적으로 공고 0건을 반환한 `COMPLETED`는 `postings: []`로 응답한다.

## 4. 성공 응답 예시

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "jobAnalysisId": "10000000-0000-0000-0000-000000000001",
    "analysisStatus": "COMPLETED",
    "userProfileId": "ecbca375-2ba2-407a-9e87-29022d2f031a",
    "userProfileVersion": 3,
    "completedAt": "2026-08-11T08:10:00Z",
    "summary": {
      "postingCount": 1,
      "completedPostingCount": 1,
      "partiallyCompletedPostingCount": 0
    },
    "postings": [
      {
        "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
        "provider": "PUBLIC_EMPLOYMENT",
        "providerPostingId": "public-job-1001",
        "companyName": "예시 공공기관",
        "originalJobTitle": "백엔드 개발자",
        "sourceUrl": "https://example.go.kr/jobs/1001",
        "conditionResult": {
          "status": "CALCULATED",
          "requiredTechnologySummary": {
            "totalRequirements": 3,
            "comparableCount": 2,
            "matchedCount": 1,
            "mismatchedCount": 1,
            "needsReviewCount": 1,
            "notApplicableCount": 0,
            "matchRate": 0.5,
            "hasMismatch": true
          },
          "preferredTechnologySummary": {
            "totalRequirements": 1,
            "comparableCount": 1,
            "matchedCount": 1,
            "mismatchedCount": 0,
            "needsReviewCount": 0,
            "notApplicableCount": 0,
            "matchRate": 1.0,
            "hasMismatch": false
          },
          "items": [
            {
              "conditionId": "condition-required-java",
              "conditionType": "TECHNOLOGY",
              "requirementLevel": "REQUIRED",
              "rawName": "Java",
              "technologyTagId": "70000000-0000-0000-0000-000000000001",
              "canonicalName": "Java",
              "status": "MATCHED",
              "reasonCode": "CANONICAL_MATCH",
              "userTechnologyTagIds": [
                "80000000-0000-0000-0000-000000000001"
              ],
              "jobEvidenceIds": ["job-required-skill-1"]
            },
            {
              "conditionId": "condition-required-kubernetes",
              "conditionType": "TECHNOLOGY",
              "requirementLevel": "REQUIRED",
              "rawName": "Kubernetes",
              "technologyTagId": "70000000-0000-0000-0000-000000000026",
              "canonicalName": "Kubernetes",
              "status": "NEEDS_REVIEW",
              "reasonCode": "USER_INPUT_MISSING",
              "userTechnologyTagIds": [],
              "jobEvidenceIds": ["job-required-skill-2"]
            }
          ]
        },
        "similarityResult": {
          "status": "CALCULATED",
          "method": "LLM_JUDGE",
          "items": [
            {
              "jobEvidenceId": "job-responsibility-1",
              "status": "CALCULATED",
              "bestMatchUserEvidenceId": "project-responsibility-1",
              "judgment": "RELATED",
              "unavailableReason": null
            }
          ],
          "modelExecution": {
            "provider": "OLLAMA",
            "model": "evaluated-model-name"
          }
        }
      }
    ],
    "evidence": [
      {
        "evidenceId": "job-required-skill-1",
        "sourceType": "JOB_POSTING",
        "sourceId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
        "excerpt": "Java 기반 서비스 개발"
      },
      {
        "evidenceId": "project-responsibility-1",
        "sourceType": "USER_PROJECT",
        "sourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
        "excerpt": "Redis 캐시와 비동기 작업을 적용했습니다."
      }
    ],
    "incompleteSections": []
  },
  "error": null,
  "timestamp": "2026-08-11T08:10:01Z"
}
```

## 5. 기술 조건 판정

Java는 Python이 추출한 `requiredSkills`와 `preferredSkills`를 기존 기술 태그
정규화·별칭 사전으로 해석한 뒤 사용자가 고정한 프로필 버전과 비교한다.

### 판정 상태

| 상태 | 의미 | 점수 포함 |
|---|---|---:|
| `MATCHED` | 공고 기술과 사용자의 보유 기술 근거가 일치 | 1점 |
| `MISMATCHED` | 사용자가 해당 기술을 미보유라고 명시적으로 확인 | 0점 |
| `NEEDS_REVIEW` | 사용자 입력 또는 기술 정규화 정보가 부족 | 제외 |
| `NOT_APPLICABLE` | 공고가 해당 조건을 요구하지 않음 | 제외 |

현재 사용자 프로필은 보유 기술만 저장하고 `미보유`와 `미입력`을 구분하지 않는다.
따라서 사용자 기술 목록에 없다는 사실만으로 `MISMATCHED`를 생성하지 않고
`NEEDS_REVIEW`와 `USER_INPUT_MISSING`을 반환한다. 명시적인 미보유 상태가 추가되기
전까지 `MISMATCHED`는 생성할 수 없다.

### 이유 코드

- `CANONICAL_MATCH`: 같은 표준 기술 태그
- `ALIAS_MATCH`: 확인된 별칭이 같은 표준 기술 태그로 연결됨
- `USER_CONFIRMED_ABSENT`: 사용자가 미보유를 명시적으로 확인함
- `USER_INPUT_MISSING`: 사용자 보유·미보유 상태를 확인할 수 없음
- `JOB_TECHNOLOGY_UNRESOLVED`: 공고 기술을 표준 태그로 해석할 수 없음
- `JOB_REQUIREMENT_NOT_SPECIFIED`: 공고에 해당 조건이 없음

부분 문자열과 임베딩 유사도만으로 표준 기술을 합치지 않는다. `Java`와 `JavaScript`,
`Spring Framework`와 `Spring Boot`처럼 관련 있지만 다른 기술은 확인된 별칭이 아니면
별개로 유지한다.

### 일치율

```text
matchRate = matchedCount / comparableCount
comparableCount = matchedCount + mismatchedCount
```

- `NEEDS_REVIEW`와 `NOT_APPLICABLE`은 분자와 분모에서 제외한다.
- `comparableCount`가 0이면 `matchRate`는 `null`이다.
- `matchRate`는 0 이상 1 이하 소수이며 프론트가 백분율로 표시한다.
- 필수 기술과 우대 기술을 분리한다.
- 필수 기술 `MISMATCHED`가 하나 이상이면 `hasMismatch=true`다.
- 우대 기술 불일치는 지원 불가가 아니라 선택적 보완으로 표시한다.

## 6. 의미 유사도 결과

의미 유사도는 [채용공고 근거 의미 유사도 내부 계약](../../contracts/job-evidence-similarity.md)의
검증을 통과한 결과만 저장·반환한다.

`similarityResult.status`:

- `CALCULATED`: 모든 대상 공고 근거를 계산함
- `PARTIALLY_CALCULATED`: 일부 항목만 계산함
- `NOT_CALCULABLE`: 사용자 프로젝트 근거가 없어 모델을 실행하지 않음
- `FAILED`: 모델 또는 내부 API 장애

`NOT_CALCULABLE`은 정상적으로 확인된 정보 부족이며 점수 0이 아니다. `FAILED`는 시스템
장애이므로 근거 부족과 구분한다. 유사도 점수는 Java 조건 판정이나 필수조건 충족 여부를
변경하지 않는다.
MVP 의미 비교 대상은 공고 `RESPONSIBILITY`와 사용자 `PROJECT_RESPONSIBILITY`뿐이다.
필수·우대 기술은 Java의 `conditionResult`가 판정한다. `method`는 후보 평가 후 확정하며
임베딩 방식은 `score`만, LLM 판정 방식은 `judgment`만 반환한다.
`overallSimilarity`는 만들지 않는다.

## 7. 근거

- 모든 조건 항목은 하나 이상의 `jobEvidenceIds`를 가진다.
- 모든 계산된 유사도 항목은 공고 근거와 사용자 프로젝트 근거 식별자를 가진다.
- `excerpt`는 판정을 확인할 수 있는 최소 문장만 반환한다.
- 사용자에게 보여줄 수 없는 내부 원문, 개인정보, 토큰과 임베딩 벡터를 반환하지 않는다.
- 응답의 모든 근거는 분석이 사용한 고정 버전에서 가져온다.

## 8. 완료와 부분 완료

제안 완료 규칙:

- 모든 공고의 조건 판정이 끝나고 유사도 영역이 `CALCULATED`,
  `PARTIALLY_CALCULATED` 또는 근거 부족으로 확인된 `NOT_CALCULABLE`이면 분석을
  `COMPLETED`로 처리한다.
- 조건 결과가 하나 이상 저장된 뒤 Python 장애로 유사도 영역이 `FAILED`이면
  `PARTIALLY_COMPLETED`로 처리한다.
- 비교 결과를 하나도 만들지 못하면 `FAILED`다.
- 검색 결과 0건은 빈 `COMPLETED`다.

`NOT_CALCULABLE`을 `COMPLETED` 안의 정상 결과로 허용할지는 12절에서 사용자 승인이
필요하다. 허용하더라도 화면은 의미 유사도를 계산한 것처럼 표시하지 않는다.

`incompleteSections` 예시:

```json
[
  {
    "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "section": "SIMILARITY_RESULT",
    "failureCode": "SEMANTIC_COMPARISON_MODEL_UNAVAILABLE"
  }
]
```

## 9. 오류

| HTTP | code | 조건 |
|---:|---|---|
| 401 | `UNAUTHORIZED` | 인증 세션 없음 |
| 404 | `JOB_ANALYSIS_NOT_FOUND` | 분석이 없거나 다른 사용자 소유 |
| 404 | `JOB_ANALYSIS_RESULT_NOT_FOUND` | 실패·취소되어 제공할 비교 결과 없음 |
| 409 | `JOB_ANALYSIS_RESULT_NOT_READY` | 분석이 아직 실행 중 |

오류도 기존 `ApiResponse<T>` 봉투를 사용하며 내부 예외, 모델 자격증명과 다른 사용자의
리소스 존재 여부를 노출하지 않는다.

## 10. 프론트 표시 규칙

- 필수 기술과 우대 기술 차트를 분리한다.
- `MATCHED`, `MISMATCHED`, `NEEDS_REVIEW`, `NOT_APPLICABLE` 개수를 함께 표시한다.
- `NEEDS_REVIEW`가 제외된 일치율이라는 사실을 표시한다.
- 의미 유사도는 조건 일치율과 별도 차트로 표시한다.
- 유사도를 합격 확률, 지원 순위 또는 실제 수행 능력으로 표현하지 않는다.
- `NOT_CALCULABLE`은 `프로젝트 근거 부족으로 확인할 수 없음`으로 표시한다.
- 결과가 0건이면 0% 차트를 만들지 않고 `검색된 공고 없음`으로 표시한다.

## 11. API 테스트

1. 현재 사용자 소유의 `COMPLETED` 결과 조회
2. `PARTIALLY_COMPLETED`에서 성공 결과와 `incompleteSections` 동시 반환
3. 실행 중 상태의 409
4. 실패·취소 결과 없음의 404
5. 다른 사용자 분석의 404
6. 공고 0건의 빈 완료 결과
7. 필수·우대 기술 결과 분리
8. `NEEDS_REVIEW`와 `NOT_APPLICABLE`의 일치율 분모 제외
9. 비교 가능 항목 0개의 `matchRate=null`
10. 사용자 미입력 기술을 `MISMATCHED`로 만들지 않음
11. 계산 불가 유사도와 모델 장애 구분
12. 모든 결과와 근거 식별자의 참조 무결성
13. 개인정보·토큰·임베딩 벡터 미노출

## 12. 구현 전 확인 필요

1. 기술 조건 비교만으로 MVP 분석을 완료할 수 있는지
2. 사용자 기술 상태에 `보유`, `미보유`, `미입력`을 언제 추가할지
3. 프로젝트 근거가 없는 `NOT_CALCULABLE`을 정상 완료로 볼지
4. 공고별 결과만 제공할지 분석 전체 집계를 추가할지
5. 의미 유사도 상위 근거를 1개만 표시할지 여러 개 표시할지
6. 사용자에게 반환할 최소 근거 문장의 최대 길이
7. 결과 보관 기간과 사용자가 결과를 삭제하는 API
8. 과거 결과의 모델 버전 변경·재계산 정책
9. 합성 fixture 평가를 통과한 LLM_JUDGE provider와 model

이 항목을 확정하기 전에는 DTO, enum, 저장 테이블, 결과 Controller와 프론트 차트를
구현하지 않는다.
