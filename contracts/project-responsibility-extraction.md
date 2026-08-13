# 프로젝트 담당 업무 근거 추출 내부 계약

상태: 확정 — 책임, 요청 제한, 사용자 확인 API와 보관 정책을 2026-08-12 결정으로 확정했다.

## 목적과 책임

Java가 사용자 선택 기술 태그를 분석 허용 목록으로 검증하고 읽기 전용 저장소 스냅숏과 함께
Python에 전달한다. Python은 저장소에서 기술 원시 이름을 결정론적으로 감지하고,
선택 기술 범위 안에서 `PROJECT_RESPONSIBILITY`(프로젝트 담당 업무) 후보를 추출한다.

- Java: 저장소·사용자 권한·선택 기술 검증, 최소 자료 전달, 표준 기술 태그 매핑,
  선택 기술 판정, 추가 기술 제안, 후보 저장과 사용자 API 제공
- Python: 전달받은 자료에서 매니페스트 파싱과 언어 감지로 기술 원시 이름·근거를 결정론적으로
  감지하고 선택 기술 범위의 담당 업무 후보를 추출한다. 기술 감지에는 LLM을 사용하지 않는다.
- 금지: 저장소 수정·코드 실행·자격증명 전달·Python의 `technologyTagId` 생성·사용자의
  직접 담당 여부 확정·선택하지 않은 기술 자동 추가

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
        "text": "public Order createOrder(...) { ... }"
      }
    ]
  }
}
```

- `selectedTechnologyTags`와 저장소 출처 URL·조회 시각·버전은 필수다.
- `selectedTechnologyTags`는 담당 업무 후보 추출 범위를 제한한다. 결정론적 기술 감지는
  선택 목록 밖의 매니페스트 의존성과 저장소 언어도 반환할 수 있다.
- Java는 허용 범위를 검증하고 개인정보를 제거한 README·설명·최소 파일만 전달한다.
- API 키, GitHub 토큰과 다른 자격증명은 요청에 포함하지 않는다.
- 허용 자료는 README·설명, 매니페스트, 설정과 제한된 소스·테스트 파일이다. Java의 파일 수집은 전송 범위를 줄이기 위한 것이며 기술 사용 여부를 확정하는 분석이 아니다.
- 비밀 설정, 빌드 결과물, 바이너리와 대용량 파일은 제외하며 코드 실행과 의존성 설치를 허용하지 않는다.
- GitHub 조회와 입력 검증은 Java가 담당하고 Python은 전달받은 자료만 분석한다.
### 요청 제한과 파일 선택

- 사용자 프로필은 기술 태그를 최대 30개 보유하며 Java는 중복을 제거한다.
- Python 요청 한 번의 `selectedTechnologyTags`는 서로 다른 태그 1개 이상 10개 이하다.
- Java는 10개 초과 선택 기술을 10개씩 나누어 호출한다. 담당 업무 후보는 근거 식별자로
  병합하고, 반복 반환된 감지 기술은 `detectedName + source` 기준으로 중복을 제거한 뒤
  `evidenceIds`를 합친다. 일부 묶음 실패는 전체 실패가 아니라 부분 완료로 기록한다.
- Python은 감지 기술을 근거 개수 내림차순, 정규화한 `detectedName` 오름차순으로 정렬해
  상위 30개만 반환한다. 이 상한은 `technology-tag-resolution`의 요청당 최대 30개와 같다.
- README는 최대 3개, 매니페스트는 최대 20개, 설정 파일은 최대 10개다.
- Python이 후보 근거로 참조하는 소스·테스트 파일은 선택 기술당 최대 3개다.
- `files[].fileType`은 `MANIFEST`, `CONFIGURATION`, `SOURCE`, `TEST`만 허용한다.
- `readmes`와 `files`는 중복 경로 제거 후 합계 30개 이하다. 같은 파일이 여러 기술과 관련되면 한 번만 전달한다.
- 파일 선택 순서는 소스, 테스트, 매니페스트, 설정, README 순서다. 이 순서는 전송 우선순위이며 기술 근거 판정이 아니다.
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
    "detectedTechnologies": [
      {
        "detectedName": "org.springframework.boot:spring-boot-starter-web",
        "source": "MANIFEST",
        "evidenceIds": ["repo-file-1"]
      },
      {
        "detectedName": "Java",
        "source": "LANGUAGE",
        "evidenceIds": ["repo-file-2"]
      }
    ],
    "responsibilityEvidenceCandidates": [
      {
        "evidenceId": "project-responsibility-1",
        "category": "PROJECT_RESPONSIBILITY",
        "text": "Spring Boot 기반 주문 API 구현",
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

- `detectedTechnologies[].detectedName`은 매니페스트가 제공한 의존성 식별자
  (group:artifact 또는 패키지명)나 감지한 언어명 원문이다. Python은 자체 기술 키워드
  목록으로 거르거나 표준 이름으로 바꾸지 않는다.
- `detectedTechnologies[].source`는 `MANIFEST` 또는 `LANGUAGE`만 허용하며,
  `evidenceIds`는 요청 스냅숏에 존재하는 근거만 참조한다.
- Python 응답에는 `technologyTagId`, `canonicalName`, `findingStatus`를 포함하지 않는다.
  담당 업무 후보도 표준 태그 ID를 반환하지 않고 입력의 `sourceEvidenceIds`만 참조한다.
- `text`는 입력 근거로 확인 가능한 최소 업무 표현이며 새로운 성과·역할을 생성하지 않는다.
- 모든 후보는 `UNCONFIRMED`이며, Java 사용자 API를 통해 브라우저에 AI 분석 미리보기로 표시한다.
- 미리보기는 저장소에서 해석한 기술·담당 업무 후보와 최소 근거를 보여주지만 최종 채용공고 비교 결과가 아니다.
- Python은 영구 저장하거나 브라우저에 직접 응답하지 않는다. Java는 저장소 버전, 후보, 최소 근거와 사용자 확인 이력을 관리한다.
- Java는 `detectedName` 목록을 [기술 태그 정규화 내부 계약](technology-tag-resolution.md)의
  `MATCHED + CANONICAL/ALIAS` 또는 `UNRESOLVED + NONE`으로 해석한다.
- Java는 `UNRESOLVED` 항목을 표준 태그 판정과 제안에서 제외한다. 유사도 기반
  `SUGGEST_CORRECTION`이나 새 별칭 자동 등록은 사용하지 않는다.
- Java는 매핑된 표준 태그가 사용자의 선택 태그에 있으면 `FOUND`, 없으면 추가 기술 제안으로
  구분한다. 선택 태그가 매핑 결과에 없으면 `NEEDS_REVIEW`로 판정한다.
- 담당 업무와 기술의 연결은 `sourceEvidenceIds`와 감지 기술의 `evidenceIds` 교집합을
  Java가 계산한다.
- `NEEDS_REVIEW`는 오류, 기술 미보유 또는 `MISMATCHED`가 아니라 **확인 필요**다.
  프론트는 [분석 결과 API 10절](../docs/api/job-analysis-result-api.md#10-프론트-표시-규칙)에
  따라 실패로 표시하거나 일치율 분모에 넣지 않는다.
- 매핑됐지만 선택하지 않은 기술은 사용자에게 추가, 무시 또는 저장소 교체 선택지를 제공한다.
  자동 추가하지 않으며 사용자의 선택 전에는 프로필이나 분석 사실로 저장하지 않는다.
- 사용자는 후보를 확인, 수정 후 확인 또는 거부할 수 있으며 상태는 `UNCONFIRMED`, `CONFIRMED`, `REJECTED`를 사용한다.
- `CONFIRMED` 후보만 의미 비교 입력으로 사용한다.
- 미확정 후보는 30일 보관한 뒤 만료하며, 확정 근거는 사용자 프로필 버전과 연결한다.
- 전체 파일 내용은 영구 저장하지 않고 출처·저장소 버전·최소 근거만 저장한다.
- 거부 후보의 원문은 삭제하고 거부 상태와 시각만 기록한다.

## 사용자 분석 미리보기 조회와 결정 API

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
    "reviewStatus": "AWAITING_USER_CONFIRMATION",
    "linkedJobAnalysisId": "10000000-0000-0000-0000-000000000001",
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
프론트엔드는 이 응답을 `AI 프로젝트 분석 미리보기`로 표시하고, 사용자가 모든 후보를 확인 또는 거부하기 전에는 최종 분석 완료로 표시하지 않는다.

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

성공하면 `200`과 갱신된 후보, `reviewCompleted`와 `resumedJobAnalysisId`를 `data`로 반환한다. 마지막 후보 결정이 아니면 `reviewCompleted=false`, `resumedJobAnalysisId=null`이다. 마지막 결정으로 검토가 끝나고 연결된 채용 분석이 있으면 `reviewCompleted=true`와 재개한 분석 식별자를 반환한다.

- 조회의 `reviewStatus`는 `AWAITING_USER_CONFIRMATION` 또는 `REVIEW_COMPLETED`다.
- `linkedJobAnalysisId`는 이 미리보기를 기다리는 채용 분석이 없으면 `null`이다.
- `decision`은 `CONFIRM` 또는 `REJECT`다.
- `CONFIRM`은 `confirmedText`가 필수이며 Unicode 코드 포인트 기준 1자 이상 500자 이하다. 수정 후 확인도 같은 요청을 사용한다.
- `REJECT`는 `confirmedText=null`이어야 하며 추출 원문을 삭제하고 거부 상태와 시각만 보관한다.
- `UNCONFIRMED`만 `CONFIRMED` 또는 `REJECTED`로 전이할 수 있고 두 상태는 최종 상태다.
- 개별 결정 요청은 후보 상태와 확인 문장만 저장한다. 후보 하나를 결정할 때마다 프로필 버전이나 Python 최종 비교를 만들지 않는다.
- 같은 추출 작업의 모든 후보가 `CONFIRMED` 또는 `REJECTED`가 된 마지막 결정에서 Java가 모든 `CONFIRMED` 근거를 포함한 새 사용자 프로필 버전을 한 번만 생성하고 추출 작업을 `REVIEW_COMPLETED`로 전이한다.
- 연결된 채용 분석이 있으면 위 트랜잭션 커밋 뒤 작업을 다시 대기열에 넣는다. Python에는 승인 boolean을 보내지 않고 사용자가 확정한 최소 근거 문장을 의미 비교 입력으로 전달한다.
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

공동 계약 테스트는 결정론적 기술 감지, 입력에 없는 근거 참조 거부, Python 응답의 표준 태그 ID 거부, 개인정보 제거 후 빈 자료,
내부 토큰 실패, 모델 장애와 같은 저장소 버전 보존을 포함한다.

## 구현 순서

1. Java 저장소 최소 파일 안전 수집·제외 사유 기록·선택 기술 범위 검증과 Python 요청 분할
2. Python 계약 스키마·추출 구현, Java 후보 저장·사용자 결정 API와 공동 계약 테스트

## Java 구현 의사코드

이 절은 구현 순서 검토용이며 실제 Java 코드가 아니다. 후보 추출은 사용자 확인보다 먼저 실행하고,
채용 분석 요청은 기존 프로필 버전으로 생성할 수 있지만 프로젝트 분석 미리보기 확인 전에는 최종 비교를 시작하지 않는다. 마지막 사용자 결정에서 확정 근거가 있으면 새 프로필 버전으로 한 번 교체해 고정한 뒤 비교를 재개한다.

```text
사용자가 프로젝트 저장소와 분석할 기술 태그 선택
→ Java가 사용자 권한·저장소·commitSha·선택 기술 검증
→ Java가 저장소 최소 자료와 선택 기술 허용 목록을 Python에 전달
→ Python이 모든 raw 기술을 결정론적으로 감지하고 선택 기술 범위의 담당 업무 근거 후보를 추출·분석
→ Java가 raw 기술을 표준 태그로 매핑하고 선택 판정·추가 제안을 생성
→ Java가 응답 범위와 계약을 검증한 뒤 UNCONFIRMED 후보 저장
→ 브라우저가 Java API로 AI 프로젝트 분석 미리보기와 최소 근거 표시
→ 사용자가 모든 후보를 확인·수정 후 확인·거부
→ Java가 CONFIRMED 근거를 포함한 새 프로필 버전을 한 번만 저장
→ 연결된 채용 분석을 재개하고 확정 근거를 Python 의미 비교 API에 전달
→ Python이 공고 업무와 확정 프로젝트 업무를 비교해 Java에 반환
→ Java가 비교 응답을 검증·저장하고 브라우저가 최종 분석 결과 표시
```

### 저장소 자료 수집

```text
prepareRepositorySnapshot(projectSourceId, profileVersion):

    현재 사용자 소유 ProjectSource를 조회한다
    ProjectSource의 고정 commitSha와 사용자 선택 기술을 읽는다
    선택 기술을 중복 제거하고 최대 30개인지 검증한다

    트랜잭션을 끝낸다

    GitHub에서 고정 commitSha의 파일 트리를 읽는다
    비밀 파일, 빌드 결과물, 바이너리와 제외 디렉터리를 제거한다
    확장자·경로·파일 종류와 제외 규칙만으로 전송 후보 파일을 고른다
    소스 → 테스트 → 매니페스트 → 설정 → README 순서로 정렬한다
    중복 경로를 제거하고 종류별·전체 파일 개수 제한을 적용한다

    각 후보 파일의 원격 크기가 102,400바이트를 넘으면 제외 사유를 기록한다
    허용 파일만 고정 commitSha에서 읽는다
    전송할 텍스트를 파일당 2,000자 이하로 제한한다
    전체 텍스트 20,000자 제한을 넘기 전에 낮은 우선순위 파일을 제외한다

    RepositorySnapshot과 제외 사유를 반환한다
```

- 기본 브랜치의 최신 파일을 다시 읽지 않고 등록된 `commitSha`만 사용한다.
- GitHub 응답이 불완전하거나 해당 SHA의 파일을 읽지 못하면 다른 버전으로 대체하지 않는다.
- Java는 `import`, 어노테이션, API 호출과 코드 로직으로 기술 사용 여부나 담당 업무를 추출·판정하지 않는다.
- 전체 파일 원문, GitHub 자격증명과 제외된 파일 내용은 저장하거나 Python에 전달하지 않는다.

### Python 분할 호출과 후보 저장

```text
extractResponsibilityCandidates(projectSourceId, profileVersion):

    snapshot = prepareRepositorySnapshot(projectSourceId, profileVersion)
    technologyBatches = 선택 기술을 최대 10개씩 분할한다
    successfulCandidates = []
    failedBatchCount = 0

    각 technologyBatch에 대해:
        request = 고정 snapshot과 분석 허용 목록인 technologyBatch로 만든다
        response = PythonProjectResponsibilityExtractionClient.extract(request)
        요청 식별자, projectSourceId, repositoryVersion을 검증한다
        detectedName, source, evidenceIds와 sourceEvidenceIds가 계약과 요청 근거에 맞는지 검증한다
        Python 응답에 technologyTagId가 없고 modelExecution과 UNCONFIRMED 상태가 맞는지 검증한다

        계약 위반이면 응답을 저장하지 않고 해당 묶음을 실패로 기록한다
        정상이면 detectedName + source 기준으로 감지 기술을 병합하고 evidenceIds를 합친다
        담당 업무 후보는 sourceEvidenceIds 기준으로 병합한다

    mergedDetections의 detectedName을 TechnologyTagResolutionService로 해석한다
    MATCHED + CANONICAL/ALIAS만 표준 태그에 연결하고 UNRESOLVED + NONE은 제외한다
    선택 태그별 FOUND/NEEDS_REVIEW를 판정한다
    매핑됐지만 미선택인 태그는 사용자 제안 후보로 만든다
    담당 업무 sourceEvidenceIds와 기술 evidenceIds의 교집합으로 관련 태그를 연결한다

    성공 묶음이 하나도 없으면 추출 실패로 기록한다
    일부 묶음만 실패하면 부분 완료와 실패 묶음을 기록한다
    모든 묶음이 성공하면 추출 완료로 기록한다

    짧은 트랜잭션에서:
        같은 projectSourceId와 repositoryVersion의 중복 후보 생성을 막는다
        UNCONFIRMED 후보, 최소 근거, 모델 실행과 만료 시각을 저장한다
```

Python 호출과 GitHub 호출 중에는 데이터베이스 트랜잭션을 유지하지 않는다. 자동 재시도는 멱등 키와
재시도 상태가 별도 확정된 경우에만 추가한다.

### 후보 조회와 사용자 결정

```text
listResponsibilityCandidates(projectSourceId):

    현재 사용자 소유 프로젝트인지 확인한다
    만료되지 않은 후보만 조회한다
    전체 파일 원문을 제외한 최소 근거와 상태를 반환한다


decideResponsibilityCandidate(candidateId, request):

    현재 사용자 소유 후보를 version과 함께 조회한다

    후보가 만료됐으면 410
    expectedVersion이 다르면 409
    같은 최종 결정과 같은 confirmedText면 현재 결과를 200으로 반환한다
    이미 다른 최종 상태이면 409

    CONFIRM이면:
        confirmedText를 1자 이상 500자 이하로 검증한다
        후보를 CONFIRMED로 전이한다
        확인 근거를 포함하는 새 UserProfileVersion을 만든다

    REJECT이면:
        confirmedText가 null인지 검증한다
        추출 원문과 최소 코드 excerpt를 삭제한다
        후보를 REJECTED로 전이하고 결정 시각만 남긴다

    후보 결정을 저장한다

    같은 extractionTask의 모든 후보가 최종 상태이면:
        모든 CONFIRMED 후보를 모은다
        CONFIRMED 후보가 있으면:
            확인 근거를 포함하는 새 UserProfileVersion을 한 번만 만든다
            연결된 JobAnalysis의 프로필 버전을 새 버전으로 한 번 교체해 고정한다
        모두 REJECTED이면:
            연결된 JobAnalysis의 기존 프로필 버전을 유지한다
        extractionTask를 REVIEW_COMPLETED로 전이한다

    후보 결정, 마지막 결정의 프로필 버전 생성과 JobAnalysis 입력 버전 고정을 한 트랜잭션으로 커밋한다

    연결된 JobAnalysis가 있으면 커밋 뒤 QUEUED로 전이한다
    갱신된 후보와 reviewCompleted 여부를 반환한다
```

동시 요청은 후보의 낙관적 잠금 버전으로 먼저 성공한 요청만 반영한다. 다른 사용자 소유 후보는
존재 여부를 노출하지 않고 `404`로 반환한다.

### 채용 분석 연결

```text
createOrResumeJobAnalysis(request):

    프로젝트 분석 미리보기가 아직 사용자 확인 전이면:
        분석 작업을 AWAITING_USER_CONFIRMATION으로 유지한다
        최종 의미 비교를 실행하지 않는다

    모든 후보 검토가 끝나면:
        CONFIRMED 후보가 있으면 마지막 결정에서 생성된 UserProfileVersion을 고정한다
        모든 후보가 REJECTED이면 기존 UserProfileVersion을 유지한다
        고정 버전에 연결된 CONFIRMED 프로젝트 담당 업무 근거만 읽는다
        확인되지 않았거나 거부·만료된 후보는 포함하지 않는다
        분석 작업을 QUEUED로 저장한다


JobAnalysisWorker.processClaimedAnalysis(jobAnalysis):

    채용공고 검색과 구조화 추출을 완료한다
    Java의 명확한 조건 판정을 수행한다
    COMPARING_EVIDENCE로 전이한다

    고정된 공고 RESPONSIBILITY와 사용자 PROJECT_RESPONSIBILITY를 만든다
    Python에 사용자 승인 상태를 보내지 않고 사용자가 확인한 PROJECT_RESPONSIBILITY 문장만 보낸다
    사용자 근거가 비어 있으면 NOT_CALCULABLE을 정상 결과로 기록한다
    근거가 있으면 PythonEvidenceSimilarityClient를 호출한다
    응답 계약을 검증한 뒤 의미 비교 결과와 모델 실행 정보를 Java에 저장한다

    일부 비교만 성공하면 PARTIALLY_COMPLETED
    모두 정상 처리되면 FINALIZING_RESULT → COMPLETED
```

`AWAITING_USER_CONFIRMATION`에서는 마지막 사용자 결정 트랜잭션에서 프로필 버전을 한 번만 교체할 수 있다. 작업이 다시 `QUEUED`가 된 뒤와 실행 중·완료된 분석에서는 고정 프로필과 결과를 변경하지 않는다.

### 구현 시작 전에 코드 수준에서 정할 항목

- 프로젝트 등록 직후 추출을 시작할지 별도의 사용자가 누르는 새로 조회 API에서 시작할지
- 허용 확장자·제외 경로·파일 유형 등 안전 수집 규칙의 저장 위치
- GitHub 파일 트리 응답이 잘린 경우의 제외 사유와 사용자 표시 상태
- 묶음 일부 실패의 저장 상태와 수동 재실행 API
- 프로젝트 담당 업무 근거를 `UserProfileVersion`에 연결할 테이블 관계와 Flyway 변경

`AWAITING_USER_CONFIRMATION`과 `REVIEW_COMPLETED` 상태, 추출 작업과 연결된 채용 분석 관계는 이번 두 단계 흐름에 포함한다. 나머지 항목은 DTO, enum, 테이블과 분기문을 작성하기 전에 설계해야 한다.

### 공식 참고 자료

- [GitHub REST API - Git Trees](https://docs.github.com/en/rest/git/trees)
- [GitHub REST API - Repository contents](https://docs.github.com/en/rest/repos/contents)
- [Spring Framework - Declarative transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html)
- [Spring Framework - REST Clients](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
