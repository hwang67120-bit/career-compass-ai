# 표준 기술 태그 검색 API

상태: MVP 확정

## 목적

AI가 매 요청마다 기술명을 새로 추측하지 않도록 Java가 표준 기술 태그와 별칭을
관리한다. 사용자는 검색 결과에서 태그를 선택하고, 목록에 없는 기술은 이후 프로필
API에서 커스텀 태그로 입력한다.

## 정책

- 다른 취업 사이트의 태그 목록을 복사하지 않는다.
- 프로젝트가 직접 관리하는 기본 태그 30개를 제공한다.
- 표준 이름, key와 별칭 검색에는 같은 정규화 규칙을 적용한다.
- 대소문자, 공백, 하이픈과 밑줄 차이는 검색에서 무시한다.
- 채용공고나 AI가 발견한 새 기술은 자동으로 표준 태그가 되지 않는다.
- 빈 검색어는 인기 순위가 아니라 서버가 지정한 기본 표시 순서로 반환한다.
- 검색어는 최대 50자, 응답은 최대 30개다.

## API

```http
GET /api/v1/technology-tags?query=k8s
```

`query`는 선택값이다. 생략하거나 공백이면 기본 표시 태그를 반환한다.

성공: `200 OK`

```json
{
  "requestId": "b9f521f2-feb7-44f7-af4d-75e814f96644",
  "data": {
    "technologyTags": [
      {
        "technologyTagId": "70000000-0000-0000-0000-000000000026",
        "key": "kubernetes",
        "displayName": "Kubernetes",
        "category": "INFRASTRUCTURE_CLOUD",
        "matchedAlias": "K8s"
      }
    ]
  },
  "error": null,
  "timestamp": "2026-07-31T07:00:00Z"
}
```

표준 이름 또는 key가 직접 일치하면 `matchedAlias`는 `null`이다. 별칭으로만 검색된
경우 응답에 실제 일치한 별칭을 표시한다.

## 카테고리

- `LANGUAGE`
- `FRAMEWORK_LIBRARY`
- `DATABASE`
- `INFRASTRUCTURE_CLOUD`
- `TOOL_PLATFORM`
- `OTHER`

## 초기 기본 태그

| 카테고리 | 태그 |
|---|---|
| LANGUAGE | Java, Python, JavaScript, TypeScript, Kotlin, Go, Rust, C, C++, C# |
| FRAMEWORK_LIBRARY | Spring Boot, Spring Framework, JPA, Hibernate, React, Vue.js, Node.js, FastAPI, Django |
| DATABASE | PostgreSQL, MySQL, Redis, MongoDB, SQL |
| INFRASTRUCTURE_CLOUD | Docker, Kubernetes, AWS, Linux |
| TOOL_PLATFORM | Git, GitHub |

## 오류

| HTTP | errorType | 의미 |
|---|---|---|
| 400 | `INVALID_TECHNOLOGY_TAG_QUERY` | 검색어가 설정된 길이를 초과함 |
| 401 | `UNAUTHORIZED` | 인증되지 않은 사용자 |

## 설정

```text
technology-tag.policy.max-query-length=50
technology-tag.policy.max-search-results=30
```

환경변수:

```text
TECHNOLOGY_TAG_MAX_QUERY_LENGTH
TECHNOLOGY_TAG_MAX_SEARCH_RESULTS
```
