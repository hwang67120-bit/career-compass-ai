# 채용공고 구조화 추출 계약

상태: **제안 — 코덱스 확인 필요**

이 계약은 MVP 흐름(`PDF 등록 → 추출·수정·확정 → 채용공고 등록 → 조건 판정+의미 분석 → 결과 화면 → 테스트 배포`) 중 "채용공고 등록"의 Java–Python 내부 API를 정의한다. [`document-extraction.md`](document-extraction.md)와 같은 원칙(계약 공동·실행 Java·AI 처리 Python)을 따르되, 다음 차이가 있다.

- 입력이 PDF가 아니라 **텍스트**다 — 파일 업로드, PII 제거 단계가 없다. 이 텍스트는 사용자가
  직접 입력하지 않고, [`job-search-tool.md`](job-search-tool.md) 계약으로 Java가 공식 채용 API
  (사람인·고용24)에서 받아온다.
- 채용공고는 공개된 회사 정보이므로 개인정보 가드레일(계약 7절 상당)이 적용되지 않는다.

함께 적용하는 문서:

- [`docs/architecture/llm-providers.md`](../docs/architecture/llm-providers.md)
- [`docs/architecture/domain-state-ownership.md`](../docs/architecture/domain-state-ownership.md)

## 1. 실행 경계

1. Java가 [`job-search-tool.md`](job-search-tool.md) 계약으로 사람인·고용24 공식 API에서
   채용공고 원문(`sourceText`)을 받는다.
2. Java가 텍스트 길이와 기본 검증을 수행한다.
3. Java가 `JobPosting`을 등록하고 별도의 `ExtractionTask`를 생성한다.
4. Java는 사용자 요청과 분리된 실행 흐름에서 이 계약의 Python API를 호출한다.
5. Python 내부 API 호출은 한 번의 동기 HTTP 요청이다.
6. 성공하면 Java가 구조화 결과를 저장하고 `ExtractionTask`를 완료 상태로 전환한다.
7. 실패하면 Java가 실패 원인을 `ExtractionTask`에 기록한다. 재시도는 새 `ExtractionTask`를 생성한다.

`JobPosting`과 `ExtractionTask`의 상태는 Java가 소유한다. Python은 데이터베이스 상태를 직접 변경하지 않는다.

## 2. 책임 경계

### Java

- 사용자 인증과 텍스트 길이·형식을 검증한다.
- `JobPosting`과 `ExtractionTask` 식별자를 생성한다.
- Python 응답을 계약 스키마로 다시 검증한다.
- 성공한 결과를 저장하고 사용자의 수정·확정 흐름을 제공한다.

### Python

- 채용공고 원문에서 직무명·필수/우대 기술을 추출한다.
- 확인할 수 없는 값은 만들지 않고, 근거 없는 항목은 응답에서 제외한다(이력서와 같은 원칙, `app/services/resume_extraction.py`의 `filter_unevidenced_candidates`에 대응).
- 사용자 계정, 작업 상태를 저장하지 않는다.

## 3. 내부 API

```http
POST /internal/v1/job-postings/extract
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

파일이 없으므로 `multipart/form-data`가 아니라 JSON 본문을 사용한다.

### 요청 본문

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `jobPostingId` | UUID 문자열 | 예 | Java가 생성한 `JobPosting` 식별자 |
| `extractionTaskId` | UUID 문자열 | 예 | 이번 실행을 나타내는 `ExtractionTask` 식별자 |
| `sourceText` | string | 예 | 공백이 아닌 문자열, 설정된 최대 길이 이하 |

## 4. 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "jobPostingId": "7b94df20-7e9f-4df7-bc90-408306e1fcd6",
    "extractionTaskId": "25a89eb8-224f-4457-ae6f-53dc32414f0d",
    "status": "EXTRACTED",
    "extraction": {
      "jobTitle": null,
      "jobTitleEvidenceIds": [],
      "requiredSkills": [],
      "preferredSkills": [],
      "evidence": []
    },
    "modelProvider": "ollama",
    "modelName": "configured-model-name"
  },
  "error": null,
  "timestamp": "2026-07-30T08:00:00Z"
}
```

`jobTitle`은 확신할 수 없으면 `null`이다(실제 확인됨 — 아래 8절 참고). `piiRemoved` 필드는 없다(개인정보 제거 단계가 없으므로).

### `JobPostingSkill`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rawName` | string | 공고에 적힌 기술 이름 |
| `evidenceIds` | string[] | 하나 이상의 근거 식별자 |

### `JobPostingEvidence`

| 필드 | 타입 | 설명 |
|---|---|---|
| `evidenceId` | string | 응답 안에서 중복되지 않는 식별자 |
| `fieldPath` | string | 연결 대상 필드 경로 |
| `value` | string | 근거가 뒷받침하는 값 |
| `sourceText` | string | 판정을 확인할 수 있는 최소 원문 범위 |

모든 후보 항목(`requiredSkills`, `preferredSkills`, `jobTitle`을 채운 경우)은 근거를 가져야 한다. 근거를 못 만든 항목은 응답에서 제외한다(빈 값으로 채우지 않음 — `document-extraction.md`에서 코덱스와 합의한 방식과 동일).

## 5. 실패 응답

`document-extraction.md` 6절과 같은 봉투 형식이다.

| HTTP | `errorType` | 원인 | `retryable` |
|---:|---|---|---:|
| 422 | `INTERNAL_TOKEN_REQUIRED` | `X-Internal-Token` 누락 | false |
| 401 | `INTERNAL_UNAUTHORIZED` | 내부 토큰 불일치 | false |
| 422 | `INVALID_EXTRACTION_REQUEST` | UUID 형식 오류, 빈 텍스트, 최대 길이 초과 | false |
| 503 | `MODEL_UNAVAILABLE` | `OllamaUnavailableError`, `GeminiUnavailableError` | true |
| 502 | `MODEL_RESPONSE_INVALID` | 모델 응답이 스키마·근거 검증을 통과하지 못함 | false |

## 6. 성능 측정

문서 추출 계약 9절과 같은 원칙 — 최적화 전에 모델 호출 시간과 전체 처리 시간을 측정한다.

## 7. 확인 필요 (병합 전 코덱스 검토 요청)

- 이 문서 전체가 **제안**이며, Java 쪽 사용자 API·`JobPosting` 저장 모델과 함께 확정해야 한다.
- 텍스트 최대 길이 설정값(Java `DOCUMENT_MAX_TEXT_LENGTH`와 별도로 둘지, 공유할지).
- `jobTitle`이 채워지지 않는 경우(8절 참고) Java가 어떻게 처리할지 — 재시도, 사용자 직접 입력 등.

## 8. 실제 검증에서 발견한 사항

`qwen2.5:latest`로 실제 호출해보면 `requiredSkills`·`evidence`는 안정적으로 채우지만, `jobTitle`은 채우지 않는 경우가 실제로 재현됐다(원문에 "백엔드 개발자를 채용합니다"처럼 직무가 명확해도). 이력서와 마찬가지로 근거 없는 값을 지어내지 않는 지침을 모델이 보수적으로 따른 것으로 보인다. 스키마·프롬프트가 계약으로 확정되지 않아 이번에는 더 튜닝하지 않았다 — `docs/architecture/llm-providers.md`의 모델 평가 방식(같은 평가 자료로 통과율 비교)을 채용공고에도 적용할 필요가 있다.
