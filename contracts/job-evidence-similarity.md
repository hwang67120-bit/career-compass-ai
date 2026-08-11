# 채용공고 근거 의미 유사도 내부 계약

상태: 제안 — 13절의 확인 필요 항목을 사용자 승인하기 전에는 구현하지 않는다.

## 1. 목적

Java가 확인한 채용공고 근거와 사용자 프로젝트 근거 사이의 의미 유사도를 Python이
임베딩으로 계산한다. 이 API는 문장 의미의 가까움을 계산할 뿐, 사용자의 기술 보유 여부,
지원 가능 여부, 합격 확률과 최종 추천 순위를 판정하지 않는다.

- 호출자: Java
- 제공자: Python
- 저장 책임: Java
- Python 책임: 임베딩 생성, 동일 차원의 근거 비교, 최고 유사 근거 반환
- Java 책임: 입력 최소화·개인정보 제거, 작업 상태, 결과 저장과 사용자 권한 확인
- 제외: 경력·학력·자격증·지역·고용 형태의 규칙 판정

Java의 `conditionResult`와 Python의 `similarityResult`는 서로 독립적이다. Python의
유사도는 Java의 `MATCHED`, `MISMATCHED`, `NEEDS_REVIEW`, `NOT_APPLICABLE`을
변경할 수 없고 두 결과를 하나의 불투명한 총점으로 합치지 않는다.

## 2. 엔드포인트와 헤더

```http
POST /internal/v1/job-evidence-similarities
Content-Type: application/json
X-Internal-Token: {INTERNAL_SERVICE_TOKEN}
X-Request-Id: {uuid}
```

- 내부 토큰은 Java와 Python이 환경변수 `INTERNAL_SERVICE_TOKEN`으로 공유한다.
- `X-Request-Id`가 없으면 Python이 UUID를 생성한다.
- 내부 토큰, 근거 원문 전체와 모델 자격증명은 일반 로그에 기록하지 않는다.

## 3. 요청

```json
{
  "comparisonTaskId": "37ac4f55-7140-4c14-a6aa-fcfc7ea5d75a",
  "jobAnalysisId": "10000000-0000-0000-0000-000000000001",
  "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
  "jobEvidence": [
    {
      "evidenceId": "job-responsibility-1",
      "category": "RESPONSIBILITY",
      "text": "대규모 트래픽을 처리하는 백엔드 API를 개발합니다."
    },
    {
      "evidenceId": "job-required-skill-1",
      "category": "REQUIRED_SKILL",
      "text": "Spring Boot"
    }
  ],
  "userEvidence": [
    {
      "evidenceId": "project-responsibility-1",
      "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
      "category": "PROJECT_RESPONSIBILITY",
      "text": "Redis 캐시와 비동기 작업을 적용해 API 응답 부하를 줄였습니다."
    },
    {
      "evidenceId": "project-technology-1",
      "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
      "category": "PROJECT_TECHNOLOGY",
      "text": "Spring Boot"
    }
  ]
}
```

### 요청 필드

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `comparisonTaskId` | UUID 문자열 | 예 | 이번 유사도 실행 식별자 |
| `jobAnalysisId` | UUID 문자열 | 예 | Java 분석 작업 식별자 |
| `jobPostingId` | UUID 문자열 | 예 | 비교 대상 공고 식별자 |
| `jobEvidence` | 배열 | 예 | 1개 이상, 요청 안에서 `evidenceId` 중복 금지 |
| `jobEvidence[].evidenceId` | string | 예 | 공백이 아닌 안정적인 근거 식별자 |
| `jobEvidence[].category` | enum | 예 | `RESPONSIBILITY`, `REQUIRED_SKILL`, `PREFERRED_SKILL` |
| `jobEvidence[].text` | string | 예 | 개인정보가 제거된 최소 근거 문장 |
| `userEvidence` | 배열 | 예 | 1개 이상, 요청 안에서 `evidenceId` 중복 금지 |
| `userEvidence[].evidenceId` | string | 예 | 공백이 아닌 안정적인 근거 식별자 |
| `userEvidence[].projectSourceId` | UUID 문자열 | 예 | 현재 사용자가 선택한 공개 프로젝트 출처 |
| `userEvidence[].category` | enum | 예 | `PROJECT_RESPONSIBILITY`, `PROJECT_TECHNOLOGY` |
| `userEvidence[].text` | string | 예 | 사용자가 확인했거나 공개 저장소에서 직접 확인한 최소 근거 |

배열 최대 개수와 각 `text` 최대 길이는 실제 공고·프로젝트 표본을 측정한 뒤 Java와
Python의 동일한 전용 설정으로 확정한다. 측정 전 임의의 제한값을 코드에 넣지 않는다.

## 4. 비교 차원

Python은 다음 조합만 비교한다.

| 공고 근거 | 사용자 근거 | `dimension` |
|---|---|---|
| `RESPONSIBILITY` | `PROJECT_RESPONSIBILITY` | `RESPONSIBILITY` |
| `REQUIRED_SKILL` | `PROJECT_TECHNOLOGY` | `TECHNOLOGY` |
| `PREFERRED_SKILL` | `PROJECT_TECHNOLOGY` | `TECHNOLOGY` |

차원이 다른 근거끼리는 비교하지 않는다. 호환되는 사용자 근거가 없으면 해당 공고 근거를
`NOT_CALCULABLE`로 반환하고 임의의 0점을 만들지 않는다.

## 5. 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "comparisonTaskId": "37ac4f55-7140-4c14-a6aa-fcfc7ea5d75a",
    "jobAnalysisId": "10000000-0000-0000-0000-000000000001",
    "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "status": "CALCULATED",
    "metric": "NORMALIZED_COSINE",
    "results": [
      {
        "jobEvidenceId": "job-responsibility-1",
        "dimension": "RESPONSIBILITY",
        "status": "CALCULATED",
        "bestMatch": {
          "userEvidenceId": "project-responsibility-1",
          "score": 0.82
        },
        "unavailableReason": null
      },
      {
        "jobEvidenceId": "job-required-skill-1",
        "dimension": "TECHNOLOGY",
        "status": "CALCULATED",
        "bestMatch": {
          "userEvidenceId": "project-technology-1",
          "score": 1.0
        },
        "unavailableReason": null
      }
    ],
    "modelExecution": {
      "stage": "EVIDENCE_EMBEDDING",
      "provider": "OLLAMA",
      "model": "configured-embedding-model-name"
    }
  },
  "error": null,
  "timestamp": "2026-08-11T08:00:00Z"
}
```

### 결과 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `CALCULATED` \| `PARTIALLY_CALCULATED` \| `NOT_CALCULABLE` | 요청 전체 계산 상태 |
| `metric` | `NORMALIZED_COSINE` | 점수 계산 방식 |
| `results` | 배열 | 입력 `jobEvidence`와 같은 개수·순서 |
| `results[].dimension` | `RESPONSIBILITY` \| `TECHNOLOGY` | 비교 차원 |
| `results[].status` | `CALCULATED` \| `NOT_CALCULABLE` | 항목별 계산 상태 |
| `results[].bestMatch` | object 또는 null | 호환되는 사용자 근거 중 가장 높은 결과 |
| `results[].bestMatch.score` | number | 제안 범위 0 이상 1 이하 |
| `results[].unavailableReason` | enum 또는 null | 계산하지 못한 이유 |
| `modelExecution` | object 또는 null | 모델 실행이 없으면 null |
| `modelExecution.provider` | `OLLAMA` | 실제 임베딩 제공자 |
| `modelExecution.model` | string | 실제 임베딩 모델 이름 |

모든 항목이 계산되면 `CALCULATED`, 계산된 항목과 계산 불가 항목이 함께 있으면
`PARTIALLY_CALCULATED`, 모든 항목이 계산 불가이면 `NOT_CALCULABLE`이다.
모델이 한 번도 실행되지 않은 경우 `modelExecution`은 null이다.

## 6. 계산 불가

```json
{
  "jobEvidenceId": "job-responsibility-1",
  "dimension": "RESPONSIBILITY",
  "status": "NOT_CALCULABLE",
  "bestMatch": null,
  "unavailableReason": "COMPATIBLE_USER_EVIDENCE_MISSING"
}
```

허용 이유:

- `COMPATIBLE_USER_EVIDENCE_MISSING`: 같은 차원의 사용자 근거가 없음
- `JOB_EVIDENCE_EMPTY_AFTER_SANITIZATION`: 안전 처리 후 공고 근거가 비어 있음
- `USER_EVIDENCE_EMPTY_AFTER_SANITIZATION`: 안전 처리 후 사용자 근거가 비어 있음

근거 부족은 모델 장애가 아니며 0점을 반환하지 않는다. Java는 이를 사용자 결과에서
`확인할 수 없음`으로 표시한다.

## 7. 점수 의미

제안 계산식:

```text
normalizedScore = clamp((cosineSimilarity + 1) / 2, 0, 1)
```

- 점수는 문장 임베딩의 상대적 의미 유사도다.
- 점수는 합격 확률, 기술 보유 여부, 수행 능력과 채용 담당자의 평가가 아니다.
- Python은 임계값으로 `MATCHED` 또는 `MISMATCHED`를 생성하지 않는다.
- Java도 유사도 점수로 명확한 조건 판정 결과를 변경하지 않는다.
- 서로 다른 임베딩 모델의 점수를 같은 척도로 직접 비교하지 않는다.

점수 정규화 방식은 13절에서 사용자 승인이 필요하다.

## 8. 저장과 재현 정보

Python은 요청과 결과를 영구 저장하지 않는다. Java는 다음 최소값을 저장한다.

- 분석·공고·비교 작업 식별자
- 입력에 사용한 근거 식별자
- 항목별 상태, 최고 유사 근거 식별자와 점수
- metric, provider, model
- 계산 시각과 근거 버전

원문 전체, 저장소 토큰, API 키, 개인정보와 임베딩 벡터 원본의 영구 저장은 별도 보관
정책이 확정되기 전에는 추가하지 않는다.

## 9. 오류

| HTTP | `errorType` | retryable | 조건 |
|---:|---|---:|---|
| 401 | `INTERNAL_UNAUTHORIZED` | false | 내부 토큰 누락 또는 불일치 |
| 422 | `INVALID_SIMILARITY_REQUEST` | false | UUID, 배열, enum, 중복 식별자 또는 텍스트 검증 실패 |
| 502 | `EMBEDDING_RESPONSE_INVALID` | false | 모델 응답 차원·숫자·유한값 검증 실패 |
| 503 | `EMBEDDING_MODEL_UNAVAILABLE` | true | Ollama 또는 임베딩 모델에 연결할 수 없음 |

오류도 공통 `requestId/data/error/timestamp` 봉투를 사용한다. 입력 검증에서 모델이
실행되지 않았거나 모델 장애로 결과를 만들지 못한 요청은 무료 이용량에서 차감하지 않는다.
MVP에서는 자동 재시도하지 않는다.

## 10. 보안과 개인정보

- Java는 사용자 소유권과 분석에 선택된 `projectSourceId`를 확인한다.
- Java는 이름, 이메일, 전화번호, 생년월일, 사진과 상세 주소를 제거한다.
- 저장소 URL, 브랜치 전체 내용과 사용자 토큰은 Python에 보내지 않는다.
- Python에는 비교에 필요한 최소 근거 문장과 식별자만 전달한다.
- 일반 로그에는 근거 문장, 내부 토큰과 임베딩 벡터를 기록하지 않는다.
- 외부 모델 폴백은 별도 승인 전 허용하지 않는다.

## 11. Java의 응답 검증

Java는 저장 전에 다음을 검증한다.

1. `requestId`, `comparisonTaskId`, `jobAnalysisId`, `jobPostingId` 일치
2. 입력한 모든 `jobEvidenceId`가 응답에 정확히 한 번 존재
3. 응답의 `userEvidenceId`가 요청에 포함된 값인지 확인
4. category에 맞는 dimension과 사용자 근거 조합인지 확인
5. enum, 점수 범위, NaN·Infinity와 null 조합 확인
6. `CALCULATED`에는 `bestMatch`가 있고 `unavailableReason`이 없는지 확인
7. `NOT_CALCULABLE`에는 `bestMatch`가 없고 허용된 이유가 있는지 확인
8. provider와 model이 비어 있지 않은지 확인

계약 위반 응답은 결과로 저장하지 않고 `DEPENDENCY_INVALID_RESPONSE`로 처리한다.

## 12. 공동 계약 테스트

1. 담당 업무와 프로젝트 업무 근거의 정상 비교
2. 필수·우대 기술과 프로젝트 기술 근거의 정상 비교
3. 차원이 다른 근거를 비교하지 않음
4. 호환되는 사용자 근거가 없는 `NOT_CALCULABLE`
5. 입력 공고 근거와 결과의 개수·순서 보존
6. 중복 근거 식별자, 빈 문자열, 잘못된 UUID와 enum 거부
7. 0, 1과 경계 밖 점수 응답 검증
8. NaN과 Infinity 응답 거부
9. 내부 토큰 누락과 불일치
10. Ollama 및 임베딩 모델 장애
11. Java와 Python을 동시에 실행한 실제 HTTP 요청

## 13. 구현 전 확인 필요

1. 엔드포인트 이름 `/internal/v1/job-evidence-similarities`
2. 사용자 프로젝트 근거의 생성·확인·버전 관리 계약
3. 프로젝트 근거 category를 두 종류로 제한할지 여부
4. cosine 점수를 0~1로 정규화하는 계산식
5. 공고 근거별 최고 1개만 반환할지 상위 여러 개를 반환할지 여부
6. 실제 표본으로 측정한 배열 개수와 텍스트 길이 제한
7. 임베딩 모델 이름, 버전 변경과 과거 결과 재계산 정책
8. 사용자 근거 부족의 `NOT_CALCULABLE`을 정상 완료로 볼지 여부

이 항목을 확정하기 전에는 DTO, enum, 저장 테이블과 Python endpoint를 구현하지 않는다.
