# 현재 작업 상태

이 문서는 Java·Python·공통 계약 작업의 현재 위치와 검증 수준을 공유한다.
`구현 완료`라는 표현 대신 실제로 통과한 가장 높은 검증 상태를 기록한다.

- 마지막 확인일: 2026-07-31
- Python 기준: `origin/python` 커밋 `42dd375`
- Java 기준: `java` 브랜치 작업 트리
- 상태 정의: 루트 `AGENTS.md`의 완료 판정 기준

## 기록 규칙

- 담당자는 작업 시작 전 자신의 영역과 파일을 기록한다.
- 각 담당자는 자신이 실행한 검증만 상태 근거로 사용한다.
- 외부 제공자 실제 호출은 제공자 단위 검증이며 Java–Python 통합 검증이 아니다.
- 계약 변경은 `제안` 또는 `확인 필요`로 표시하고 사용자 확정 전 구현 기준으로 사용하지 않는다.
- 상태가 바뀌면 근거가 되는 테스트·HTTP 요청·브라우저 확인·배포 환경을 함께 기록한다.

## 현재 담당과 편집 범위

| 영역 | 담당 | 현재 편집 범위 | 충돌 방지 |
| --- | --- | --- | --- |
| Java 인증 | Codex | `backend-java`의 GitHub OAuth, 인증 응답, 보안 설정과 테스트 | 현재 작업을 전달하기 전 같은 파일을 수정하지 않음 |
| Python 분석 | Python 담당 AI | `ai-python`의 제공자·PDF·임베딩·유사도·재정렬 | Java 담당은 직접 수정하지 않고 계약 영향만 보고 |
| 공통 계약 | 사용자 확인 후 지정 담당 | `contracts/*` | `제안`을 사용자 확인 없이 `MVP 확정`으로 바꾸지 않음 |
| 공통 문서 | 사용자 또는 지정 담당 | `README.md`, `AGENTS.md`, `docs/current-work.md` | 한 시점에 한 담당자만 공통 영역 수정 |

## Python 현재 검증 상태

아래 표는 `origin/python` 커밋 `42dd375`의 코드·README·테스트 파일을 확인한 결과다.
Java와 Python 서버를 함께 실행한 계약 테스트가 없으므로 `INTEGRATION_TESTED` 이상인 기능은 없다.

| 기능 | 구현 위치 | 현재 상태 | 확인 근거 | 다음 단계 |
| --- | --- | --- | --- | --- |
| 상태 확인 API | `app/health/`, `tests/health/test_health.py` | `UNIT_TESTED` | FastAPI 라우트와 Python API 테스트 존재 | Java에서 실제 HTTP 호출 후 계약 확인 |
| 내부 서비스 토큰 검사 | `app/guardrails/internal_auth.py` | `UNIT_TESTED` | 누락·오류·정상 토큰 테스트 존재 | Java 헤더 전송 구현 후 실제 서버 연결 |
| PDF 페이지별 텍스트 추출 | `app/services/pdf_extraction.py` | `UNIT_TESTED` | `tests/services/test_pdf_extraction.py` 존재 | 추출 API 라우트와 개인정보 제거 연결 |
| Ollama 구조화 추출 | `app/providers/ollama.py` | `UNIT_TESTED` | 로컬 Ollama 실제 호출 테스트 존재 | 추출 서비스와 HTTP API에 연결 |
| Gemini 구조화 추출 | `app/providers/gemini.py` | `UNIT_TESTED` | 가상 채용공고를 사용한 실제 Gemini 호출 테스트 존재 | 추출 서비스와 HTTP API에 연결 |
| Ollama 임베딩 | `app/providers/embedding.py` | `UNIT_TESTED` | `tests/providers/test_ollama_embedding.py` 존재 | 분석 실행 API에서 호출 |
| Gemini 임베딩 | `app/providers/embedding.py` | `UNIT_TESTED` | `tests/providers/test_gemini_embedding.py` 존재 | 분석 실행 API에서 호출 |
| 코사인 유사도 | `app/services/similarity.py` | `UNIT_TESTED` | `tests/services/test_similarity.py` 존재 | 확정 프로필·채용공고 계약으로 연결 |
| 후보 재정렬 | `app/services/reranking.py` | `UNIT_TESTED` | 단위 테스트와 실제 임베딩 기반 테스트 존재 | 분석 실행 API와 결과 계약으로 연결 |
| 저장소 코드 근거 추출(결정론적, LLM 없음) | `app/services/repository_evidence.py`, `app/providers/github_repository.py` | `UNIT_TESTED` | 순수 로직 단위 테스트 9건 + 실제 GitHub API 호출 테스트(`octocat/Hello-World`) 5건 통과 | Java가 owner·repository·commitSha를 넘기는 API 라우트로 연결. 매니페스트·키워드·언어 확장자 목록은 확인 필요 |
| 수기 입력 기술과 저장소 근거 분리 | `app/schemas/technical_evidence.py`, `app/services/manual_skill_evidence.py`, `app/services/technical_profile.py` | `UNIT_TESTED` | 단위 테스트 9건 통과 | Java가 사용자 수기 입력 기술 목록을 넘기는 API 라우트로 연결 |
| 희망 직무 기반 채용공고 검색어 생성 | `app/services/job_search_keywords.py`, `app/providers/ollama.py`·`gemini.py`의 `generate_job_search_keyword_suggestions` | `UNIT_TESTED` | 순수 로직 8건 + 실제 Gemini 호출 1건 통과. 실제 Ollama 호출 1건은 로컬 Ollama 미실행으로 미확인(코드 문제 아님) | Java가 희망 직무·기술 목록을 넘기는 API 라우트로 연결. 검색어는 사용자에게 보여주기만 하고 다른 조회 API에 자동 전달하지 않음(2026-07-30 확정) |
| 기술 태그 유사도 판단(오타·표기 차이 감지) | `app/schemas/skill_tag_match.py`, `app/services/skill_tag_matching.py` | `UNIT_TESTED` | 순수 로직 10건(네트워크 없음) + 실제 Gemini 임베딩 호출 3건 통과(단독 실행 시). 스위트 전체를 한 번에 돌리면 Gemini 무료 등급 요청 제한으로 이따금 실패(코드 문제 아님) | Java가 고정 태그 목록(캐시된 임베딩 포함)·후보 태그를 넘기는 API 라우트로 연결. 임계값(0.72)·margin(0.05)은 표본이 작아 확인 필요 |

## 기술 태그 정규화 (2026-07-31 사용자 확인)

자기소개(수기 입력·이력서)에 적힌 표현은 추상적이고, 채용공고에 적힌 표현은 구체적이라 그냥
자유 텍스트로 비교하면 정확하지 않다. 고정 태그 목록을 두고 그 목록 기준으로 비교하기로 했다.

- **고정 태그 목록 출처**: 새로 수집하거나 별도 목록을 만들지 않고, 지금까지·앞으로 채용공고
  구조화 추출(`contracts/job-posting-extraction.md`)이 실제로 뽑아낸 `rawName`을 모아서 만든다.
  즉 채용 시장에서 실제로 쓰이는 표현이 곧 고정 태그가 된다. 목록 저장·관리는 Java 책임.
- **오타·표기 차이 판단은 Python이 임베딩으로 한다**: 문자열 거리(Levenshtein 등)가 아니라
  의미 임베딩 유사도로 판단한다 — "JS"/"JavaScript"처럼 글자로는 멀어도 뜻은 같은 경우를 잡기
  위해서다. `app/services/skill_tag_matching.py`의 `match_skill_tag`가 후보 태그 하나를 받아
  `EXACT_MATCH`/`SUGGEST_CORRECTION`/`NO_MATCH`를 반환한다.
- **Python은 값을 직접 안 바꾼다**: `SUGGEST_CORRECTION`은 권고일 뿐이고, 실제 정규화는 사용자
  확인(수정 허락)을 받은 뒤 Java·사용자가 처리한다(`AGENTS.md` "AI 추출 결과는 사용자가 확인하기
  전까지 확정 프로필로 사용하지 않는다").
- **대응하는 고정 태그가 없으면**(`NO_MATCH`) 그 기술은 조건 판정·유사도 비교에서 제외를 권고한다.
  사용자가 입력한 값 자체를 지우지는 않는다.
- 임계값 0.72는 실제 Gemini 임베딩으로 오타·번역 표기 쌍(0.76~0.83)과 실제로 다른 기술 쌍
  (0.56~0.66) 사이 간격에 놓은 값이다(표본 7쌍, 확인 필요 — 더 많은 예시·다른 모델로 재평가 필요).

**2026-07-31 추가 문제 제기와 대응 — 고정 태그가 많아질수록 정확도가 떨어지는 문제**:
고정 태그가 계속 쌓이면(채용공고를 처리할 때마다 늘어남) 절대 유사도 임계값만으로는 오탐이
늘어난다 — 후보가 많을수록 "그 많은 것 중 우연히 가장 비슷한 것"의 유사도 자체가 통계적으로
올라가는 경향이 있기 때문이다. 세 가지로 대응했다.

1. **캐시 구조**: `match_skill_tag`가 고정 태그들의 임베딩(`canonical_vectors`)을 매번 다시
   계산하지 않고 캐시된 값을 그대로 받도록 바꿨다 — 후보 태그 하나만 새로 임베딩한다. 고정
   태그가 새로 생길 때 한 번만 임베딩해서 저장해두는 건 Java 책임이다.
2. **재정렬 재사용**: 새 정렬 로직을 만들지 않고 이미 검증된 `app/services/reranking.py`의
   `rerank_candidates`를 그대로 재사용해 후보 태그 대 고정 태그 전체를 순위 매긴다.
3. **1·2위 유사도 차이(margin) 추가**: 절대 임계값(0.72)뿐 아니라 1위와 2위의 유사도 차이가
   `MARGIN_THRESHOLD`(0.05) 이상일 때만 `SUGGEST_CORRECTION`을 준다. 실제 Gemini 임베딩으로
   확인한 결과, 진짜 오타 쌍(스프링부트→Spring Boot, Postgre→PostgreSQL)은 margin이 0.148~0.156인
   반면 대응 태그가 없거나 무관한 경우(JS, 우쿨렐레, Node.js)는 0.002~0.021로 훨씬 작았다(표본
   5건). `SkillTagMatch.margin` 필드로 이 값을 같이 반환해 Java가 확신 정도를 볼 수 있게 했다.

이 margin 값도 고정 태그 15개짜리 목록으로만 확인한 것이라, 실제로 수백~수천 개로 늘어난
뒤에는 재평가가 필요하다(확인 필요로 남김 — 실제 데이터 없이는 완전히 검증할 수 없다).

이 기능은 아직 Java–Python 계약이 없다. Java가 고정 태그 목록(과 캐시된 임베딩)·후보 태그를
넘기는 API 라우트를 설계할 때 계약을 함께 작성한다.

## Python 다음 작업

2026-07-30 사용자 확인: 비교 범위를 줄였다. 비교 근거는 **이력서 PDF가 아니라 GitHub 저장소 코드와
수기 입력 기술 키워드**를 사용한다 — 이 두 출처는 개인정보를 포함하지 않기 때문이다.
아래는 Python이 구현할 전체 범위 10개 중 남은 항목이다(사용자 확정, 우선순위 순서). 1~3번(저장소
근거 추출, 수기 입력 분리, 검색어 생성)은 구현·단위 테스트를 마쳐 위 검증 상태 표로 옮겼다 — Java
API 라우트 연결 전까지는 완료로 보지 않는다.

| 우선순위 | 작업 | 계약 상태 | 시작 조건 |
| ---: | --- | --- | --- |
| 1 | 채용공고 구조화 추출 API 확정 | `contracts/job-posting-extraction.md` 제안 | 계약 MVP 확정. `contracts/job-search-tool.md`가 넘기는 `jobPostings[].sourceText`를 그대로 입력으로 쓸 수 있는지 확인 |
| 2 | 사용자 경험·주요 업무 임베딩(저장소+수기 키워드 기준) | 확인 필요 | 위 검증된 근거 추출 결과(`TechnicalEvidenceExtraction`)를 입력으로 사용 |
| 3 | 기술·프로젝트 의미 유사도 계산 | 기존 `app/services/similarity.py` 재사용 가능 | 2번 임베딩 결과 |
| 4 | 적합한 채용공고 재정렬 | 기존 `app/services/reranking.py` 재사용 가능 | 3번 결과 |
| 5 | 부족 기술과 추천 이유 생성 | 확인 필요 | 근거 있는 값만 생성하는 규칙(`AGENTS.md` "사실, 추정과 미확인 구분") 적용 |
| 6 | 근거 없는 기술·경력 제거 | 확인 필요 | 5번과 함께 근거 검증 규칙 확정 |
| 7 | 모델 성능·토큰·단계별 처리시간 측정 | 확인 필요 | 측정할 단계 범위와 기록 위치(로그·별도 저장소) 확정 |

**확인 필요 — Gemini에 실제 사용자 값 전달**: `generate_job_search_keyword_suggestions`는 Gemini
무료 등급 데이터 제한 정책 적용 대상이다. 희망 직무·기술명이 이력서 원문만큼 민감하지는 않지만,
실제 사용자 값을 Gemini에 보내도 되는지 아직 확인되지 않았다. 확정 전까지는 `OllamaProvider`만
실제 서비스에 연결하고, Gemini는 모델 비교·인터페이스 검증 용도로만 쓴다.

Python 모델 이름은 현재 연동 검증용 임시값이며 실제 채택 모델은 확인이 필요하다.

**확인 필요 — 이력서 PDF 파이프라인 처리**: 기존에 구현한 이력서 PDF 업로드·추출·개인정보 제거
(`contracts/document-extraction.md`, `app/documents/`)가 이 비교 기능에서 완전히 빠지는 것인지,
아니면 다른 용도로 남겨두고 비교 기능만 저장소+수기 키워드로 가는 것인지 아직 확정되지 않았다.
확정 전까지 이 파이프라인의 기존 구현·테스트는 유지하고 삭제하지 않는다.

1번(채용공고 구조화 추출 API 확정)은 채용공고 원문(`sourceText`)을 어떻게 확보하는지와 맞물려 있다.
그 원문 확보 방법이 끝나기 전까지 Python은 임시 샘플 채용공고 텍스트로 임베딩·유사도·재정렬을
연결해 두고, 모델 정확도(어떤 모델·프롬프트가 근거 있는 결과를 내는지)를 계속 검증한다. 이 임시
연결은 `제안` 상태이며 실제 Java 요청으로 계약 검증을 마치기 전까지 완료로 보지 않는다.

## 채용공고 검색·전체 분석 파이프라인 (2026-07-31 갱신 — 옛 "채용공고 외부 조회" 절 대체)

2026-07-31 사용자 확인: 이 문서에 있던 "채용공고 외부 조회"(사용자 URL 입력 → Java 허용 도메인
서버사이드 조회) 계획은 폐기됐다. Codex가 이미 다음 두 계약을 `develop`에 제안 상태로 merge했다
(코드 변경 없음, 사용자 확정 전 구현 기준 아님).

- [`contracts/job-search-tool.md`](../contracts/job-search-tool.md) — Python은 임의 인터넷 접속 권한이
  없고, 분석 중 공고 검색이 필요하면 Java가 제공하는 내부 API(`POST /internal/v1/tools/job-search`)만
  호출한다. Java가 **사람인 공식 채용정보 API**, **고용24 Open API** 순서로 호출해 공식 데이터만
  가져온다 — 사용자 URL 입력도, 잡코리아·게임잡·원티드·알바몬·취업24 같은 화이트리스트 도메인
  직접 조회도, SSRF 방어 로직도 이제 필요 없다.
- [`docs/api/developer-job-analysis-api.md`](../docs/api/developer-job-analysis-api.md) — 사용자
  분석 프로필(희망 직무·수기 기술), 프로젝트 출처 선택, 분석 작업(상태·이벤트·취소·부분 완료·결과)의
  전체 사용자 API. Python 책임을 "저장소 근거 추출, 검색 기준 후보 생성, 공고 구조화, 임베딩, 의미
  유사도와 재정렬"로 명시한다 — 우리 1~7번과 정확히 일치한다. 조건 판정(Java, 규칙 기반)과 의미
  유사도(Python, 근거 기반)를 하나의 점수로 합치지 않는 원칙도 명시돼 있다.

3번(검색어 생성)과의 관계: `job-search-tool.md`의 `skillKeywords` 필드는 "사용자 입력 또는 저장소
근거로 확인된 기술"이라고 명시돼 있다 — 즉 1·2번(검증된 기술) 그대로를 쓰고, 3번이 LLM으로 만든
동의어·영문 표기(`GENERATED`)는 여기 안 들어간다. 3번은 계속 사용자에게 보여주는 용도로만 남는다
(계약에 이미 확인됨, 별도 확인 필요 아님).

아직 남은 것: Java가 이 두 계약을 실제로 구현해야 Python이 실제 공고 데이터를 받을 수 있다
(Codex 쪽 "구현 순서" 1~7단계, 이번 merge는 계약 문서만). Python이 `POST
/internal/v1/tools/job-search`를 호출하는 클라이언트를 만드는 것 자체는 이 문서에 아직 없는 새
작업이다 — Java 구현이 끝나고 실제 연결을 시작할 때 범위를 정한다.

## Java 현재 검증 상태

| 기능 | 현재 상태 | 확인 근거 | 다음 단계 |
| --- | --- | --- | --- |
| GitHub OAuth와 고정 사용자 우회 제거 | `UNIT_TESTED` | Java 전체 테스트, 로그인 전 `NONE`, GitHub 로그인 시작 `302` 확인 | 실제 Client ID·Secret으로 브라우저 로그인 |
| Python 문서 추출 내부 클라이언트 | `UNIT_TESTED` | Java 21 전체 테스트 89개 통과, 내부 토큰·multipart·요청 ID·성공·오류 봉투 계약 단위 검증 | Java와 Python을 함께 실행해 실제 HTTP 계약 검증 |
| 텍스트 문서 등록 | `INTEGRATION_TESTED` | Java·PostgreSQL HTTP 및 Postman 확인 이력 | GitHub 로그인 세션 적용 후 재검증 |
| 공개 GitHub 저장소 등록 | `INTEGRATION_TESTED` | 실제 GitHub 주소 HTTP·Postman 확인 이력 | GitHub 로그인 세션·CSRF 적용 후 재검증 |

인증 변경은 아직 `java` 작업 트리에 커밋되지 않았다. 다른 담당자는 전달 커밋이 생기기 전
`backend-java` 인증 파일과 공통 인증 문서를 수정하지 않는다.

## 통합 차단 요소

- 실제 GitHub OAuth App의 Client ID·Secret 발급과 로컬 callback 등록
- Java–Python 공통 예제 JSON 계약 테스트
- PDF 업로드부터 분석 결과까지 연결된 브라우저 흐름

## 상태 변경 기록

| 날짜 | 영역 | 이전 상태 | 새 상태 | 근거 |
| --- | --- | --- | --- | --- |
| 2026-07-29 | Python 내부 분석 기능 | 구현 완료로 통칭 | `UNIT_TESTED` | 원격 Python 브랜치의 코드·테스트·README 확인 |
| 2026-07-29 | Java GitHub OAuth | 구현 완료로 통칭 | `UNIT_TESTED` | 자동 테스트와 로컬 HTTP 확인, 실제 GitHub 브라우저 로그인 미실행 |
| 2026-07-29 | Java Python 문서 추출 클라이언트 | `IMPLEMENTED` | `UNIT_TESTED` | Java 21에서 대상 클라이언트 테스트와 전체 89개 테스트를 캐시 없이 실행해 통과 |
| 2026-07-31 | 채용공고 외부 조회 | URL 화이트리스트·SSRF 방어(제안) | 폐기, `contracts/job-search-tool.md`(사람인·고용24 공식 API)로 대체 | Codex가 develop에 제안 계약 merge, 사용자 확인 |
