# 프로젝트 담당 업무 근거 추출 내부 계약

상태: 제안 — 확인 필요 항목을 확정하기 전에는 DTO(데이터 전달 객체), 엔드포인트와 저장 구조를 구현하지 않는다.

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
- 배열·문자열·파일 크기 제한은 실제 표본 측정 후 양쪽 설정으로 확정한다.

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

## 오류와 공동 계약 테스트

| HTTP | errorType | retryable |
|---:|---|---:|
| 401 | `INTERNAL_UNAUTHORIZED` | false |
| 422 | `INVALID_PROJECT_RESPONSIBILITY_EXTRACTION_REQUEST` | false |
| 502 | `PROJECT_RESPONSIBILITY_EXTRACTION_RESPONSE_INVALID` | false |
| 503 | `PROJECT_RESPONSIBILITY_EXTRACTION_MODEL_UNAVAILABLE` | true |

공동 계약 테스트는 정상 후보, 입력에 없는 태그·근거 참조 거부, 개인정보 제거 후 빈 자료,
내부 토큰 실패, 모델 장애와 같은 저장소 버전 보존을 포함한다.

## 확인 필요

1. 담당 업무 근거를 README·프로젝트 설명까지만 볼지 허용된 코드 파일까지 볼지
2. 선택 기술과 저장소 실제 기술이 불일치할 때 빈 후보, 확인 필요 후보 또는 요청 오류 중 어떤 결과를 사용할지
3. Java가 저장소 자료를 전달할지 현재 `repository_evidence.py`처럼 Python이 GitHub를 직접 조회할지와 전환 순서
4. 사용자 후보 확인·수정·거부 API와 후보 보관 기간
