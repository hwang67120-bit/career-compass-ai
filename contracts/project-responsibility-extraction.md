# 프로젝트 담당 업무 근거 추출 내부 계약

상태: 확정 — 책임, 요청 제한, 사용자 확인 API와 보관 정책을 2026-08-12 결정으로 확정했다.

## 목적과 책임

Java가 사용자 선택 기술 태그와 읽기 전용 저장소 스냅숏을 Python에 전달하고, Python은 기술 근거와
`PROJECT_RESPONSIBILITY`(프로젝트 담당 업무) 후보를 추출한다.

- Java: 저장소·사용자 권한 검증, 선택 태그와 최소 자료 전달, 후보 저장과 사용자 확인 관리
- Python: 전달받은 자료 안에서 선택 기술의 사용 근거와 담당 업무 후보 추출
- 금지: 저장소 수정·코드 실행·자격증명 전달·사용자의 직접 담당 여부 확정·선택하지 않은 기술을 사용자 기술로 확정

## 엔드포인트와 요청

```http
POST /internal/v1/project-responsibility-extractions
Content-Type: application/json
X-Internal-Token: {INTERNAL_SERVICE_TOKEN}
X-Request-Id: {uuid}
```

```json
{
  "extractionTaskId": "8e5f6c65-ad0e-49a4-bb48-f2a6164ca21c",
  "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
  "selectedTechnologyTags": [
    { "technologyTagId": "70000000-0000-0000-0000-000000000001", "canonicalName": "Spring Boot" }
  ],
  "repositorySnapshot": {
    "sourceUrl": "https://github.com/example/sample-project",
    "fetchedAt": "2026-08-12T08:00:00Z",
    "repositoryVersion": "0123456789abcdef0123456789abcdef01234567",
    "description": "Sample backend project",
    "readmes": [
      { "evidenceId": "repo-readme", "path": "README.md", "text": "Spring Boot로 API를 구현했습니다." }
    ],
    "files": [
      {
        "evidenceId": "repo-file-1",
        "path": "src/main/java/example/OrderService.java",
        "fileType": "SOURCE",
        "relatedTechnologyTagIds": ["70000000-0000-0000-0000-000000000001"],
        "text": "public Order createOrder(...) { ... }"
      }
    ]
  }
}
```

- `selectedTechnologyTags`와 저장소 출처 URL·조회 시각·버전은 필수다.
- Java는 허용 범위를 검증하고 개인정보를 제거한 README·설명·최소 파일만 전달한다.
- API 키, GitHub 토큰과 다른 자격증명은 요청에 포함하지 않는다.
- 허용 자료는 README·설명, 매니페스트, 설정과 선택 기술 관련 핵심 소스·테스트 파일이다.
- 비밀 설정, 빌드 결과물, 바이너리와 대용량 파일은 제외하며 코드 실행과 의존성 설치를 허용하지 않는다.
- GitHub 조회와 입력 검증은 Java가 담당하고 Python은 전달받은 자료만 분석한다.
### 요청 제한과 파일 선택

- 사용자 프로필은 기술 태그를 최대 30개 보유하며 Java는 중복을 제거한다.
- Python 요청 한 번의 `selectedTechnologyTags`는 서로 다른 태그 1개 이상 10개 이하다.
- Java는 10개 초과 기술을 10개씩 나누어 호출하고 `technologyTagId` 기준으로 결과를 합친다. 일부 묶음 실패는 전체 실패가 아니라 부분 완료로 기록한다.
- README는 최대 3개, 매니페스트는 최대 20개, 설정 파일은 최대 10개다.
- 선택 기술을 실제 참조하는 소스·테스트 파일은 기술당 최대 3개다.
- `files[].fileType`은 `MANIFEST`, `CONFIGURATION`, `SOURCE`, `TEST`만 허용한다.
- `readmes`와 `files`는 중복 경로 제거 후 합계 30개 이하다. 같은 파일이 여러 기술과 관련되면 한 번만 전달한다.
- 파일 선택 순서는 실제 소스 사용, 테스트, 매니페스트, 설정, README 순서다.
- Java가 조회하는 개별 파일은 102,400바이트 이하며, 초과 파일은 Python에 전달하지 않는다.
- Python에 전달하는 각 `text`는 Unicode 코드 포인트 기준 2,000자 이하다.
- `description`, `readmes[].text`, `files[].text`의 합계는 Unicode 코드 포인트 기준 20,000자 이하다.
- 전체 한도 초과 시 우선순위 밖 파일을 추가 호출로 나누지 않고 제외한다.
- 제외 사유는 `FILE_SIZE_LIMIT_EXCEEDED`, `FILE_COUNT_LIMIT_EXCEEDED`, `TOTAL_TEXT_LIMIT_EXCEEDED`로 Java가 기록한다.
- 제외 때문에 선택 기술 근거가 부족하면 실패나 미보유가 아니라 `NEEDS_REVIEW`로 처리한다.
- 개수·크기 제한은 Java와 Python 설정으로 관리하고 운영 적용 전 qwen2.5 입력 크기 평가로 재검토한다.

## 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "extractionTaskId": "8e5f6c65-ad0e-49a4-bb48-f2a6164ca21c",
    "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
    "repositoryVersion": "0123456789abcdef0123456789abcdef01234567",
    "technologyEvidenceCandidates": [
      {
        "technologyTagId": "70000000-0000-0000-0000-000000000001",
        "canonicalName": "Spring Boot",
        "findingStatus": "FOUND",
        "evidenceIds": ["repo-readme"],
        "confirmationStatus": "UNCONFIRMED"
      }
    ],
    "responsibilityEvidenceCandidates": [
      {
        "evidenceId": "project-responsibility-1",
        "category": "PROJECT_RESPONSIBILITY",
        "text": "Spring Boot 기반 주문 API 구현",
        "relatedTechnologyTagIds": ["70000000-0000-0000-0000-000000000001"],
        "sourceEvidenceIds": ["repo-readme", "repo-file-1"],
        "confirmationStatus": "UNCONFIRMED"
      }
    ],
    "modelExecution": {
      "stage": "PROJECT_RESPONSIBILITY_EXTRACTION",
      "provider": "OLLAMA",
      "model": "evaluated-model-name"
    }
  },
  "error": null,
  "timestamp": "2026-08-12T08:00:10Z"
}
```

- 후보는 요청의 선택 기술 태그와 입력 근거 식별자만 참조한다.
- `text`는 입력 근거로 확인 가능한 최소 업무 표현이며 새로운 성과·역할을 생성하지 않는다.
- 모든 후보는 `UNCONFIRMED`이며, 사용자 확인 뒤 Java가 별도 프로필 근거로 확정한다.
- Python은 영구 저장하지 않는다. Java는 저장소 버전, 후보, 최소 근거와 사용자 확인 이력을 관리한다.
- 선택 기술 근거를 찾으면 `findingStatus=FOUND`, 찾지 못하면 `findingStatus=NEEDS_REVIEW`로 반환한다.
- `NEEDS_REVIEW`는 오류, 기술 미보유 또는 `MISMATCHED`를 의미하지 않는다.
- 선택하지 않은 기술은 사용자 기술로 자동 추가하지 않는다.
- 사용자는 후보를 확인, 수정 후 확인 또는 거부할 수 있으며 상태는 `UNCONFIRMED`, `CONFIRMED`, `REJECTED`를 사용한다.
- `CONFIRMED` 후보만 의미 비교 입력으로 사용한다.
- 미확정 후보는 30일 보관한 뒤 만료하며, 확정 근거는 사용자 프로필 버전과 연결한다.
- 전체 파일 내용은 영구 저장하지 않고 출처·저장소 버전·최소 근거만 저장한다.
- 거부 후보의 원문은 삭제하고 거부 상태와 시각만 기록한다.

## 사용자 후보 조회와 결정 API

모든 API는 공통 `requestId/data/error/timestamp` 응답 형식을 사용한다. 요청에 `userId`를 받지 않고
현재 인증 사용자가 소유한 프로젝트 후보만 처리한다. 다른 사용자 소유 자원도 `404`로 응답한다.

### 후보 조회

```http
GET /api/v1/project-sources/{projectSourceId}/responsibility-candidates
```

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "projectSourceId": "9894e7f7-a523-4d02-a9ef-44fe0eb9a77b",
    "repositoryVersion": "0123456789abcdef0123456789abcdef01234567",
    "candidates": [
      {
        "candidateId": "46538954-ef88-4dc5-bc68-c71f18886cd8",
        "category": "PROJECT_RESPONSIBILITY",
        "extractedText": "Spring Boot 기반 주문 API 구현",
        "confirmedText": null,
        "status": "UNCONFIRMED",
        "version": 1,
        "relatedTechnologyTags": [
          { "technologyTagId": "70000000-0000-0000-0000-000000000001", "canonicalName": "Spring Boot" }
        ],
        "sourceEvidence": [
          { "evidenceId": "repo-file-1", "path": "src/main/java/example/OrderService.java", "excerpt": "public Order createOrder(...) { ... }" }
        ],
        "createdAt": "2026-08-12T08:00:10Z",
        "expiresAt": "2026-09-11T08:00:10Z",
        "decidedAt": null
      }
    ]
  },
  "error": null,
  "timestamp": "2026-08-12T08:01:00Z"
}
```

전체 파일 원문은 반환하지 않고 판정을 확인할 수 있는 최소 근거만 반환한다. 만료 후보는 목록에서 제외한다.

### 확인·수정 후 확인·거부

```http
PUT /api/v1/project-responsibility-candidates/{candidateId}/decision
```

```json
{
  "expectedVersion": 1,
  "decision": "CONFIRM",
  "confirmedText": "Spring Security 기반 JWT 인증 및 접근 권한 처리"
}
```

성공하면 `200`과 후보 조회 항목과 같은 형태의 갱신된 후보를 `data`로 반환한다.
- `decision`은 `CONFIRM` 또는 `REJECT`다.
- `CONFIRM`은 `confirmedText`가 필수이며 Unicode 코드 포인트 기준 1자 이상 500자 이하다. 수정 후 확인도 같은 요청을 사용한다.
- `REJECT`는 `confirmedText=null`이어야 하며 추출 원문을 삭제하고 거부 상태와 시각만 보관한다.
- `UNCONFIRMED`만 `CONFIRMED` 또는 `REJECTED`로 전이할 수 있고 두 상태는 최종 상태다.
- 확인된 근거와 새 사용자 프로필 버전은 한 트랜잭션으로 저장한다. `CONFIRMED` 근거만 의미 비교에 사용한다.
- 같은 결정과 문장의 재전송은 기존 결과를 `200`으로 반환한다. 다른 결정·문장 또는 `expectedVersion` 불일치는 `409`다.
- 미확정 후보는 생성 후 30일이 지나면 만료되며 결정 요청은 `410`이다.

| HTTP | errorType | 조건 |
|---:|---|---|
| 400 | `INVALID_PROJECT_RESPONSIBILITY_DECISION` | 결정값, 확인 문장 또는 요청 형식 오류 |
| 401 | `UNAUTHORIZED` | 인증되지 않은 요청 |
| 404 | `PROJECT_SOURCE_NOT_FOUND` | 프로젝트가 없거나 다른 사용자 소유 |
| 404 | `PROJECT_RESPONSIBILITY_CANDIDATE_NOT_FOUND` | 후보가 없거나 다른 사용자 소유 |
| 409 | `PROJECT_RESPONSIBILITY_CANDIDATE_VERSION_CONFLICT` | 후보 버전 불일치 |
| 409 | `PROJECT_RESPONSIBILITY_CANDIDATE_STATE_CONFLICT` | 이미 다른 최종 상태로 결정됨 |
| 410 | `PROJECT_RESPONSIBILITY_CANDIDATE_EXPIRED` | 미확정 보관 기간 만료 |
사용자 API 테스트는 소유자 조회, 다른 사용자 404, 확인·수정 후 확인·거부, 동일 요청 멱등성,
버전·상태 충돌, 30일 만료와 후보 확정·프로필 버전 생성의 트랜잭션 원자성을 포함한다.


## 오류와 공동 계약 테스트

| HTTP | errorType | retryable |
|---:|---|---:|
| 401 | `INTERNAL_UNAUTHORIZED` | false |
| 422 | `INVALID_PROJECT_RESPONSIBILITY_EXTRACTION_REQUEST` | false |
| 502 | `PROJECT_RESPONSIBILITY_EXTRACTION_RESPONSE_INVALID` | false |
| 503 | `PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE` | true |

공동 계약 테스트는 정상 후보, 입력에 없는 태그·근거 참조 거부, 개인정보 제거 후 빈 자료,
내부 토큰 실패, 모델 장애와 같은 저장소 버전 보존을 포함한다.

## 구현 순서

1. Java 저장소 최소 파일 수집·제외 사유 기록과 Python 요청 분할
2. Python 계약 스키마·추출 구현, Java 후보 저장·사용자 결정 API와 공동 계약 테스트
