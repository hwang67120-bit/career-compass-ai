# 프로젝트 문서

로컬 `docs`는 코드 구현 시 사용하는 프로젝트 기준 문서를 보관한다.
Notion의 `현재 사용` 문서를 변경하면 관련 로컬 문서도 같은 작업에서 동기화한다.

## 문서 목록

- [분석 계층 책임과 문서 동기화 기준](./analysis-responsibility-boundaries.md)
  - Java 조건 판정
  - Python 의미 유사도
  - Java–Python 가드레일
  - 조건 일치율
  - MVP 이력서 PDF와 공개 GitHub 저장소 입력 정책
  - 온라인 추론과 오프라인 학습
  - 현재 구현 상태와 문서 변경 이력
- [Postman API 확인](../postman/README.md)

## 도메인 API 명세

이 절은 현재 Java 백엔드에 구현된 사용자 도메인 API만 다룬다.
Actuator, 내부 GitHub REST 호출과 아직 구현되지 않은 API는 포함하지 않는다.

### API 목록

| 도메인 | 기능 | Method | Endpoint |
| --- | --- | --- | --- |
| 인증 | GitHub 로그인 시작 | `GET` | `/oauth2/authorization/github` |
| 인증 | 현재 로그인 사용자 조회 | `GET` | `/api/v1/auth/me` |
| 인증 | CSRF 토큰 조회 | `GET` | `/api/v1/auth/csrf` |
| 인증 | 로그아웃 | `POST` | `/api/v1/auth/logout` |
| 사용자 문서 | 텍스트 문서 등록 | `POST` | `/api/v1/documents` |
| 프로젝트 출처 | 공개 GitHub 저장소 등록 | `POST` | `/api/v1/project-sources/github` |

개발 환경의 기본 주소는 Postman 환경 파일의 `baseUrl`을 사용한다.
현재 Linux 개발 환경은 `http://100.68.47.24:8080`이다.

### 인증

`prod` 프로필은 GitHub OAuth 로그인과 서버 세션 쿠키를 사용한다.
비밀번호·이메일·GitHub 저장소 권한은 수집하지 않으며, GitHub 숫자 사용자 ID와
내부 사용자 UUID의 연결만 저장한다.

1. 브라우저에서 `GET /oauth2/authorization/github`를 연다.
2. GitHub 로그인·동의 후 `/login/oauth2/code/github` callback으로 돌아온다.
3. 서버가 GitHub 사용자 ID를 확인하고 `UserAccount`와 `ExternalIdentity`를 조회하거나 생성한다.
4. 로그인 세션을 만들고 브라우저 확인 화면 `/`로 이동한다.
5. 이후 보호 API는 `JSESSIONID` 세션 쿠키로 사용자를 확인한다.

운영 실행에는 다음 환경변수가 필요하다.

```text
SPRING_PROFILES_ACTIVE=prod
GITHUB_OAUTH_CLIENT_ID=...
GITHUB_OAUTH_CLIENT_SECRET=...
SESSION_COOKIE_SECURE=true
```

로컬 HTTP에서 OAuth 로그인을 확인할 때만 `SESSION_COOKIE_SECURE=false`를 사용하고,
운영에서는 HTTPS와 `true`를 사용한다.

현재 Linux 개발 서버용 GitHub OAuth App callback은
`http://100.68.47.24:8080/login/oauth2/code/github`로 등록한다.
운영 배포에서는 별도의 OAuth App과 HTTPS callback을 사용한다.

세션 인증의 상태 변경 요청은 CSRF 검증이 필요하다.

1. 세션 쿠키를 유지한 상태로 `GET /api/v1/auth/csrf`를 호출한다.
2. 응답의 `headerName`과 `token`을 확인한다.
3. `POST`, `PATCH`, `DELETE` 요청에 `X-CSRF-TOKEN: {token}` 헤더를 보낸다.

`dev`, `prod` 프로필은 GitHub OAuth 로그인을 사용한다.
`test` 프로필만 테스트 설정의 고정 UUID를 사용하며 `TEST_USER_ID` 환경변수는 요구하지 않는다.

- 로그인 세션이 없으면 `401 UNAUTHORIZED`
- 인증됐지만 허용되지 않은 경로이면 `403 FORBIDDEN`
- CSRF 토큰이 없거나 일치하지 않는 상태 변경 요청은 `403 FORBIDDEN`
- `/actuator/health`는 운영 상태 확인을 위해 인증 대상에서 제외
- 세션 쿠키와 GitHub 액세스 토큰은 Python Worker나 외부 LLM 제공자로 전달하지 않음
- GitHub 액세스 토큰은 데이터베이스에 저장하지 않음

### 브라우저 확인 화면

Java 서버의 `/` 경로에서 로그인과 자료 등록 상태를 브라우저로 확인할 수 있다.

- 개발·운영 프로필: 첫 화면에서 GitHub OAuth 로그인을 시작하고, 성공하면 `/`로 돌아온다.
- 테스트 프로필: 테스트 설정의 고정 사용자로 자료 등록 화면을 검증한다.
- 문서 등록: 현재 도메인 API 범위에 맞춰 이력서·포트폴리오 텍스트를 등록한다. PDF 업로드 UI는 파일 업로드 API 구현 후 추가한다.
- GitHub 등록: 공개 저장소 URL을 실제 GitHub API로 검증하고 저장소명, 기본 브랜치, 커밋 SHA를 표시한다.
- 상태 표시: 서버 응답에 따라 등록 전, 등록 중, 등록 완료, 등록 실패를 구분한다.

화면은 별도 프론트 서버 없이 Spring Boot 정적 리소스로 제공되므로 Java 서버와 같은 출처에서 API를 호출한다.
개발·운영 프로필의 변경 요청은 `/api/v1/auth/csrf`에서 받은 CSRF 토큰을 요청 헤더에 포함한다.

### 공통 응답

모든 도메인 API 응답은 다음 구조를 사용한다.

| 필드 | 타입 | 설명 |
| --- | --- | --- |
| `requestId` | UUID | 응답 단위 추적 식별자 |
| `data` | Object 또는 `null` | 정상 처리 결과 |
| `error` | Object 또는 `null` | 오류 정보 |
| `timestamp` | OffsetDateTime | 응답 생성 시각 |

정상 응답은 `data`가 존재하고 `error`가 `null`이다.

```json
{
  "requestId": "f85cf40d-3994-454c-aedd-a310d8b3e938",
  "data": {},
  "error": null,
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

실패 응답은 `data`가 `null`이고 `error`가 존재한다.

| 오류 필드 | 타입 | 설명 |
| --- | --- | --- |
| `errorType` | String | 클라이언트가 분기할 오류 식별자 |
| `message` | String | 사용자에게 표시할 오류 메시지 |
| `fieldErrors` | Array | 잘못된 요청 필드와 이유 |
| `retryable` | Boolean | 같은 요청을 나중에 다시 시도할 수 있는지 여부 |

```json
{
  "requestId": "9d74f739-18f2-4f0b-ad5b-1c593aa94214",
  "data": null,
  "error": {
    "errorType": "INVALID_INPUT",
    "message": "입력 내용을 확인해 주세요.",
    "fieldErrors": [
      {
        "fieldName": "repositoryUrl",
        "message": "GitHub 저장소 주소를 입력해 주세요."
      }
    ],
    "retryable": false
  },
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

## 사용자 문서 도메인

### 텍스트 문서 등록

```http
POST /api/v1/documents
Content-Type: application/json
```

현재 API는 PDF 파일 업로드 전 단계의 JSON 텍스트 등록 API다.

#### 요청

| 필드 | 타입 | 필수 | 허용값·규칙 |
| --- | --- | --- | --- |
| `documentType` | String | 예 | `RESUME`, `PORTFOLIO` |
| `text` | String | 예 | 공백이 아닌 텍스트, `DOCUMENT_MAX_TEXT_LENGTH` 이하 |

```json
{
  "documentType": "RESUME",
  "text": "Java와 Spring Boot를 사용한 백엔드 프로젝트 경험"
}
```

#### 성공 응답

```http
201 Created
Location: /api/v1/documents/{documentId}
```

| `data` 필드 | 타입 | 설명 |
| --- | --- | --- |
| `documentId` | UUID | 등록된 문서 식별자 |
| `documentType` | String | `RESUME` 또는 `PORTFOLIO` |
| `documentStatus` | String | 현재 `REGISTERED` |
| `createdAt` | Instant | 등록 시각 |

```json
{
  "requestId": "5ba1c7ac-1898-4416-9050-105418bd79cb",
  "data": {
    "documentId": "072c9f6f-d375-4856-ae7a-cfce1182ce67",
    "documentType": "RESUME",
    "documentStatus": "REGISTERED",
    "createdAt": "2026-07-28T00:00:00Z"
  },
  "error": null,
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

#### 오류

| HTTP | `errorType` | 조건 | 재시도 |
| ---: | --- | --- | --- |
| 400 | `INVALID_INPUT` | 문서 종류·텍스트 누락 또는 잘못된 JSON | 아니요 |
| 401 | `UNAUTHORIZED` | 현재 사용자를 확인할 수 없음 | 아니요 |
| 413 | `PAYLOAD_TOO_LARGE` | 설정된 문서 텍스트 길이 초과 | 아니요 |

## 프로젝트 출처 도메인

### 공개 GitHub 저장소 등록

```http
POST /api/v1/project-sources/github
Content-Type: application/json
```

사용자가 지정한 공개 GitHub 저장소를 읽기 전용으로 확인한다.
URL 문법만 검사하지 않고 GitHub API에서 공개 저장소, 기본 브랜치와 현재 `commitSha`를 확인한 뒤 저장한다.

#### 요청

| 필드 | 타입 | 필수 | 허용값·규칙 |
| --- | --- | --- | --- |
| `repositoryUrl` | String | 예 | `https://github.com/{owner}/{repository}` 형식의 공개 저장소 |

허용 예시:

```json
{
  "repositoryUrl": "https://github.com/octocat/Hello-World"
}
```

다음 입력은 허용하지 않는다.

- `http` 주소
- `github.com`이 아닌 호스트 또는 유사 도메인
- 사용자 정보, 사용자 지정 포트, 쿼리와 프래그먼트가 포함된 주소
- 저장소 하위 페이지 주소
- 비공개·비활성·존재하지 않는 저장소

후행 `/`와 `.git`은 제거한 정규 URL로 저장한다.

#### 성공 응답

```http
201 Created
Location: /api/v1/project-sources/{projectSourceId}
```

| `data` 필드 | 타입 | 설명 |
| --- | --- | --- |
| `projectSourceId` | UUID | 등록된 프로젝트 출처 식별자 |
| `repositoryUrl` | String | 정규화된 GitHub 저장소 URL |
| `repositoryFullName` | String | `{owner}/{repository}` |
| `defaultBranch` | String | 등록 시점의 기본 브랜치 |
| `commitSha` | String | 등록 시점 기본 브랜치의 최신 커밋 식별자 |
| `status` | String | 현재 `REGISTERED` |

```json
{
  "requestId": "f85cf40d-3994-454c-aedd-a310d8b3e938",
  "data": {
    "projectSourceId": "c7444fb5-0c6f-468c-b98d-ae05b6d0acd1",
    "repositoryUrl": "https://github.com/octocat/Hello-World",
    "repositoryFullName": "octocat/Hello-World",
    "defaultBranch": "master",
    "commitSha": "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d",
    "status": "REGISTERED"
  },
  "error": null,
  "timestamp": "2026-07-28T09:00:00+09:00"
}
```

#### 오류

| HTTP | `errorType` | 조건 | 재시도 |
| ---: | --- | --- | --- |
| 400 | `INVALID_INPUT` | `repositoryUrl` 누락 또는 공백 | 아니요 |
| 400 | `INVALID_GITHUB_REPOSITORY_URL` | 허용되지 않은 GitHub URL 형식 | 아니요 |
| 401 | `UNAUTHORIZED` | 현재 사용자를 확인할 수 없음 | 아니요 |
| 404 | `GITHUB_REPOSITORY_UNAVAILABLE` | 공개 저장소를 확인할 수 없음 | 아니요 |
| 429 | `GITHUB_RATE_LIMITED` | GitHub API 요청 한도 도달 | 예 |
| 503 | `GITHUB_SERVICE_UNAVAILABLE` | GitHub 장애·잘못된 응답·거부된 리다이렉트 | 오류 원인에 따라 다름 |

### 저장 정보

`ProjectSource`는 다음 값을 현재 사용자 소유로 저장한다.

- 정규화된 저장소 URL
- 저장소 전체 이름
- 기본 브랜치
- 등록 시점의 `commitSha`
- `REGISTERED` 상태
- 생성 시각

등록 실패 요청은 `ProjectSource`로 저장하지 않는다.

## 현재 미구현 API

다음 기능은 정책 또는 계약이 아직 완성되지 않아 이 명세의 구현 API에 포함하지 않는다.

- PDF 이력서 업로드와 추출 작업 생성
- 문서 추출 결과 조회·수정·확정
- 등록된 문서와 프로젝트 출처 조회·삭제
- GitHub 저장소 수동 새로 조회
- 이전 `commitSha`와 현재 버전 비교 및 `NO_CHANGE`
- 프로젝트 후보 확인·수정·확정
- 분석 작업 생성과 결과 조회

## 참고 문서

- [Spring MVC 주석 기반 Controller](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Spring Boot REST Client](https://docs.spring.io/spring-boot/reference/io/rest-client.html)
- [GitHub REST 저장소 API](https://docs.github.com/en/rest/repos/repos)
- [GitHub REST 커밋 API](https://docs.github.com/en/rest/commits/commits)
- [Postman Collection 가져오기](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-data/)

## 불일치 처리

로컬 프로젝트 문서와 Notion의 `현재 사용` 문서가 다르면 임의로 구현하지 않는다.
차이를 `확인 필요`로 보고하고 두 문서를 동기화한 뒤 작업을 계속한다.
