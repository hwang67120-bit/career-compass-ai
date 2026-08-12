# 프로젝트 담당 업무 근거 추출 내부 계약

상태: 부분 확정 — 책임·처리·보관 정책은 확정했으며 요청 제한과 사용자 확인 API 세부 계약은 구현 전에 확정한다.

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
    "readme": { "evidenceId": "repo-readme", "text": "Spring Boot로 API를 구현했습니다." },
    "files": [
      {
        "evidenceId": "repo-file-1",
        "path": "src/main/java/example/OrderService.java",
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
- 실제 배열·문자열·파일 크기 제한은 구현 전에 양쪽 계약으로 확정한다.

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

## 오류와 공동 계약 테스트

| HTTP | errorType | retryable |
|---:|---|---:|
| 401 | `INTERNAL_UNAUTHORIZED` | false |
| 422 | `INVALID_PROJECT_RESPONSIBILITY_EXTRACTION_REQUEST` | false |
| 502 | `PROJECT_RESPONSIBILITY_EXTRACTION_RESPONSE_INVALID` | false |
| 503 | `PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE` | true |

공동 계약 테스트는 정상 후보, 입력에 없는 태그·근거 참조 거부, 개인정보 제거 후 빈 자료,
내부 토큰 실패, 모델 장애와 같은 저장소 버전 보존을 포함한다.

## 구현 전 확인 필요

1. 배열·문자열·파일 개수와 크기 제한
2. 사용자 후보 조회·확인·수정·거부 API의 요청, 응답, 상태 코드와 권한
