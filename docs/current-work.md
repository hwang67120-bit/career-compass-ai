# 현재 작업 상태

이 문서는 Java·Python·공통 계약 작업의 현재 위치와 검증 수준을 공유한다.
`구현 완료`라는 표현 대신 실제로 통과한 가장 높은 검증 상태를 기록한다.

- 마지막 확인일: 2026-08-04
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
| 저장소 코드 근거 추출(결정론적, LLM 없음) | `app/services/repository_evidence.py`, `app/services/manifest_parsers.py`, `app/providers/github_repository.py` | `UNIT_TESTED` | 순수 로직 단위 테스트 21건(매니페스트 파서 12건 포함) + 실제 GitHub API 호출 테스트(`octocat/Hello-World`) 5건 통과 | Java가 owner·repository·commitSha를 넘기는 API 라우트로 연결. 키워드·언어 확장자 목록은 확인 필요 |
| 수기 입력 기술과 저장소 근거 분리 | `app/schemas/technical_evidence.py`, `app/services/manual_skill_evidence.py`, `app/services/technical_profile.py` | `UNIT_TESTED` | 단위 테스트 9건 통과 | Java가 사용자 수기 입력 기술 목록을 넘기는 API 라우트로 연결 |
| 희망 직무 기반 채용공고 검색어 생성 | `app/services/job_search_keywords.py`, `app/providers/ollama.py`·`gemini.py`의 `generate_job_search_keyword_suggestions` | `UNIT_TESTED` | 순수 로직 8건 + 실제 Gemini 호출 1건 통과. 실제 Ollama 호출 1건은 로컬 Ollama 미실행으로 미확인(코드 문제 아님) | Java가 희망 직무·기술 목록을 넘기는 API 라우트로 연결. 검색어는 사용자에게 보여주기만 하고 다른 조회 API에 자동 전달하지 않음(2026-07-30 확정) |
| 기술 태그 유사도 판단(오타·표기 차이 감지) | `app/schemas/skill_tag_match.py`, `app/services/skill_tag_matching.py` | `UNIT_TESTED` | 순수 로직 10건(네트워크 없음) + 실제 Gemini 임베딩 호출 3건 통과(단독 실행 시). 스위트 전체를 한 번에 돌리면 Gemini 무료 등급 요청 제한으로 이따금 실패(코드 문제 아님) | Java가 고정 태그 목록(캐시된 임베딩 포함)·후보 태그를 넘기는 API 라우트로 연결. 임계값(0.72)·margin(0.05)은 표본이 작아 확인 필요 |
| 저장소 README 조회 | `app/services/repository_readme.py` | `UNIT_TESTED` | 순수 로직 6건(네트워크 없음) + 실제 GitHub API 호출 테스트(`octocat/Hello-World`) 2건 통과 | Java API 라우트로 연결 시 `repository_evidence`와 트리 조회를 공유하도록 배선 |
| 사용자 경험·주요 업무 임베딩(README+검증된 기술) | `app/services/user_profile_embedding.py` | `UNIT_TESTED` | 순수 로직 6건 + 오케스트레이션 2건(가짜 provider, 네트워크 없음) 통과 | Java API 라우트로 연결. README 문자 상한(4000자)은 임시값이라 확인 필요 |
| 채용공고 임베딩 + 사용자·채용공고 의미 유사도 계산 | `app/services/job_posting_embedding.py`, 기존 `app/services/similarity.py` 재사용 | `UNIT_TESTED` | 순수 로직 3건 + 오케스트레이션 2건(네트워크 없음) + 실제 Gemini 임베딩으로 사용자 경험 vs 채용공고 유사도 첫 end-to-end 검증(백엔드 공고 0.821 > 프론트엔드 공고 0.627) | Java API 라우트로 연결(1번 계약 확정 후 실제 채용공고 데이터로 재검증) |
| 적합한 채용공고 재정렬 | `app/services/job_posting_ranking.py`, 기존 `app/services/reranking.py` 재사용 | `UNIT_TESTED` | 가짜 provider 오케스트레이션 2건(네트워크 없음) + 실제 채용공고 5건(백엔드 Java/Spring 0.821, 백엔드 Python/FastAPI 0.706, 게임 서버 Java 0.671, 프론트엔드·카페 매니저는 최소 유사도 0.65 미달로 제외)으로 실제 Gemini 임베딩 순위 검증 | Java API 라우트로 연결(1번 계약 확정 후). 최소 유사도(0.65)는 이번 예시로 확인한 값이라 실제 서비스 값은 확인 필요 |
| 부족 기술과 추천 이유 생성(결정론적, LLM 없음) | `app/schemas/job_fit_summary.py`, `app/services/job_fit_summary.py` | `UNIT_TESTED` | 순수 로직 7건(네트워크 없음) 통과 | Java API 라우트로 연결. 자연어 문장은 안 만들고 일치·유사도 구조화 데이터만 반환 — 문장화는 프론트엔드·Java 몫(2026-08-01 확정). 기술명 일치는 정확 문자열 비교라 동의어·오타는 놓침(확인 필요, `skill_tag_matching.py` 연결 전까지) |
| 근거 없는 기술·경력 제거 | `app/services/job_posting_extraction.py`(`validate_evidence`·`filter_unevidenced_candidates`), `app/services/resume_extraction.py`(동일 이름 함수) | `UNIT_TESTED` | `test_job_posting_extraction.py`, `test_resume_extraction.py`에 이미 테스트 존재(오늘 세션 이전부터 구현·검증됨, 2026-08-01 확인만 함) | 이미 구현 완료 — 새로 할 일 없음 |
| Python 개별 호출부 처리시간 기초 계측 | `app/services/performance_tracking.py` | 타이머 자체: `UNIT_TESTED`(순수 로직 4건, 네트워크 없음). 개별 함수 적용: `UNIT_TESTED`(기존 테스트가 계측 삽입 후에도 회귀 없이 통과). 실제 GitHub 저장소 데이터로 로그 확인: `NOT_TESTED`. Java–Python 연결: `NOT_TESTED`. 동시 요청 로그 구분: `NOT_IMPLEMENTED`. 저장소·공고 하나의 전체 분석 시간: `NOT_IMPLEMENTED` | 계약(`document-extraction.md` 9절)이 요구하는 전체 구간 중 지금은 일부(모델 호출·GitHub 조회)만 계측한다. **아직 없음**: PDF 텍스트 추출 시간, 개인정보 제거 시간, Python 내부 API 전체 처리 시간, Java에서 측정하는 Python 왕복 시간, 성공/실패(outcome) 구분, 요청 식별자(`requestId` 등) 연결. 이 항목들은 Java 분석 API·실제 GitHub 데이터와 연결할 때 후속 작업으로 진행(2026-08-01 리뷰 반영) |

**2026-08-01 리팩터링 — 매니페스트 파싱에 표준 파서 도입**: 저장소 근거 추출이 매니페스트 파일을
파일 형식과 무관하게 문자열 검색(줄 단위 키워드 매칭)하던 방식은 이미 있는 표준 파서를 안 쓰고
있었다는 문제 제기(2026-08-01)에 따라, 형식에 맞는 파서로 교체했다(`app/services/manifest_parsers.py`).

- `package.json` → `json.loads` (표준 라이브러리)
- `pom.xml` → `defusedxml.ElementTree`(공격 가능한 외부 저장소 내용을 파싱하므로 표준
  `xml.etree.ElementTree`보다 안전한 버전을 선택)
- `requirements.txt`, `pyproject.toml`의 PEP 621 의존성 → `packaging.requirements.Requirement`
- `pyproject.toml`, `Cargo.toml` → `tomli`(Python 3.11 미만, 3.11부터는 표준 `tomllib`로 자동 전환)
- `build.gradle`(.kts), `go.mod`는 표준 파서가 없어 정규식으로 좌표·모듈 경로만 뽑는 방식을 유지
  (여전히 확인 필요 — 전체 줄이 아니라 실제 좌표 문자열만 대상으로 해서 이전보다 오탐은 줄었다)

문자열 검색 방식이 실제로 오탐을 만들 수 있었다는 것도 테스트로 확인했다 — `package.json`의
`scripts` 필드에 `"react-scripts build"`가 있으면 이전 방식은 React를 근거로 오인했을 것이다.

## 채용공고 구조화 추출 모델 비교 — jobTitle 미채움 문제 조사 (2026-08-03)

`contracts/job-posting-extraction.md` 8절이 재현한 문제(원문에 직무가 명확해도 모델이
`jobTitle`을 채우지 않는 경우)를 이력서용과 같은 방식(`evaluation/model_comparison.py`)으로
재평가했다. 채용공고용 fixture 7개(제목 헤딩으로 명시 5개 + 문장 속에만 언급 1개 + 언급 없음
1개, `tests/fixtures/job_postings/`)를 준비하고, 스키마·근거 검증 외에 "근거는 있는데
`jobTitle`이 `null`인 경우"를 구분하는 `job_title_missing` 결과 종류를 추가한
`evaluation/job_posting_model_comparison.py`로 후보 모델 3개(qwen2.5, exaone3.5, llama3.2) ×
fixture 7개 × 반복 3회(총 63회)를 실제 로컬 Ollama로 호출해 확인했다(1회차 실행, 로그
`evaluation/job_posting_model_comparison_raw.log`).

| 모델 | 통과율 | 평균 시간 |
| --- | ---: | ---: |
| `qwen2.5:latest` | 86%(18/21) | 32.0s |
| `exaone3.5:latest` | 67%(14/21) | 46.7s |
| `llama3.2:latest` | 14%(3/21) | 11.8s |

- **이력서에서 100% 통과로 채택된 `exaone3.5`가 채용공고에서는 67%로 떨어졌다** — 이력서용
  모델 선정 결과가 채용공고 추출에 그대로 적용되지 않는다는 걸 실제로 확인했다
  (`llm-providers.md`의 이력서 채택 절차를 채용공고에도 별도로 적용해야 함).
- **반복 3회 중 결과 종류(성공/실패)가 갈린(flaky) 조합은 1개뿐**이다 — `exaone3.5` +
  `frontend_react.txt`(3회 중 1회만 `job_title_missing`, 나머지 2회는 성공). 나머지 20개
  조합은 3회 내내 결과가 동일했다.
- **2026-08-03 원인 확인(정정)**: `qwen2.5`가 `title_in_sentence_only.txt`에서 보인 근거 검증
  실패의 원인을 추가로 조사했다. 처음엔 "다른 fixture를 먼저 처리한 뒤에만 나타나는 순서
  의존적 현상"으로 추정했으나, "선행 호출 있음"과 "선행 호출 없이 단독 호출"을 같은 스크립트
  안에서 바로 이어 비교했더니 **둘 다 똑같이 실패**해서 이 가설은 틀렸다고 확인했다. 이어서
  `ollama ps`로 로드 상태를 확인하고 `ollama stop`으로 4회 강제 재로드해 같은 fixture를
  매번 첫 요청으로 호출했는데 **4회 모두 성공**했다 — "재로드마다 결과가 랜덤하게 고정된다"는
  가설도 틀렸다. 지금까지 모은 데이터 15건을 종합하면 원인은 둘 다가 아니라 **"같은 모델
  로드 세션 안에서 이 fixture 전에 다른 내용의 요청을 이미 처리했는가"**였다 — 갓 로드된
  모델에 이 fixture가 첫 요청이면 9/9 성공(단독 디버그 호출 5회 + 강제 재로드 4회), 같은
  세션에서 다른 채용공고를 먼저 처리한 뒤 호출하면 6/6 실패(1차 비교·3-repeat 비교·순서
  테스트 A·B)였다. 표본이 15건이라 완전히 확정된 규칙은 아니지만(확인 필요), 재현성 있는
  패턴이다.
  **운영 영향**: 이건 평가 스크립트만의 문제가 아니라 **실제 서비스에서도 재현될 수 있다** —
  같은 Ollama 모델을 내리지 않고 채용공고를 연속 처리하면 이후 요청의 근거 검증이 앞선
  요청들의 영향으로 계속 실패할 수 있다. 매 요청마다 모델을 내렸다 올리는 건 성능상
  비현실적이고, 같은 오염된 세션 안에서는 재시도해도 다시 실패할 수 있어 단순 재시도만으로는
  해결이 안 될 수 있다.
- **2026-08-03 추가 확인**: 같은 세션 오염 패턴이 `exaone3.5`에도 있는지 문제 fixture
  3개(세션 내내 계속 실패한 `backend_java_spring.txt`·`no_job_title_stated.txt`, 세션 안에서
  결과가 갈렸던 `frontend_react.txt`)를 각각 강제 재로드 후 첫 요청으로 3회씩 호출해 확인했다.
  결과는 fixture마다 달랐다 — **세션 오염이 모델의 실패를 전부 설명하지는 않는다.**
  - `backend_java_spring.txt`: 재로드 후 첫 요청 3/3 성공 — `qwen2.5`와 같은 세션 오염 패턴
    (다른 요청을 먼저 처리한 뒤에만 실패)
  - `no_job_title_stated.txt`: 재로드 후에도 3/3 똑같이 실패(매번 같은 근거 ID `E002`
    불일치) — 세션과 무관하게 **이 모델·이 입력 조합의 진짜 결함**으로 보인다
  - `frontend_react.txt`: 재로드 후 3/3 성공 — 원래 세션에서 봤던 1회 실패는 세션 오염이
    아니라 빈도 낮은 잔여 비결정성으로 보이며, 정확한 원인은 미확인
- **2026-08-03 추가 확인 — `llama3.2`는 세션 오염 패턴이 없다**: 실패 fixture 중 하나
  (`ai_ml_engineer.txt`, 원래 세션에서도 llama3.2로는 첫 호출부터 실패했었음)와 유일하게
  통과한 `title_in_sentence_only.txt`를 강제 재로드 후 첫 요청으로 각각 3회씩 호출했다.
  `ai_ml_engineer.txt`는 재로드 후에도 3/3 완전히 같은 오류(근거 `E2` 유령 참조)로 실패했고,
  `title_in_sentence_only.txt`는 3/3 그대로 성공했다 — 둘 다 원래 세션의 결과와 정확히
  같아서 세션 상태와 무관함을 확인했다. `llama3.2`의 낮은 통과율(14%)은 세션 오염이 아니라
  이력서 평가 때부터 봤던 것과 같은 **순수 모델 결함**(근거 유령 참조를 반복 생성)이다.

**세 모델의 실패 원인 요약**: `qwen2.5`는 순수 세션 오염, `exaone3.5`는 세션 오염·진짜
모델 결함·저빈도 잔여 비결정성이 혼재, `llama3.2`는 세션 오염 없이 순수 모델 결함이다.
- `jobTitle`이 없는 게 정답인 fixture(`no_job_title_stated.txt`)를 올바르게 처리한 건
  `qwen2.5`뿐이었다. `exaone3.5`·`llama3.2`는 둘 다 근거를 지어내다 검증에 걸렸다
  (`evidence_invalid`) — "제목이 없으면 조용히 `null`" 대신 "억지로 근거를 만듦" 쪽이었다.
  `exaone3.5`는 위 확인대로 세션과 무관하게 이 입력에서 매번 같은 방식으로 실패한다.
- `llama3.2`는 이력서 평가와 마찬가지로 근거 유령 참조가 압도적이라 후보에서 사실상 제외.

**다음 단계(확인 필요, Python이 임의로 결정할 수 없음)**

- 세션 오염 패턴(순수 모델 결함과 구분)을 더 큰 표본으로 재확인 — 몇 번째 요청부터 실패로
  바뀌는지, 다른 fixture·다른 모델에서도 같은 구분이 나오는지
- `exaone3.5` + `no_job_title_stated.txt`처럼 세션과 무관한 진짜 모델 결함은 프롬프트·스키마
  조정으로 고칠 여지가 있는지 별도로 시도해볼 것(세션 오염 완화책과는 다른 해법이 필요)
- **2026-08-03 사용자 확인·구현 완료 — 완화책 방향 결정**: 매 요청 재로드(비용이 너무 큼)와
  `num_parallel` 등 Ollama 설정 조정(효과 검증 안 됨, 새 리스크)은 채택하지 않는다. 대신
  **근거 검증(`validate_evidence`)이 실패했을 때만 Python이 모델을 강제로 언로드하고 1회만
  재시도**하는 방식으로 갔다 — 재로드 직후 첫 요청은 이번 조사에서 9/9 성공했으므로 딱 필요한
  경우에만 비용을 쓴다. `exaone3.5` + `no_job_title_stated.txt`처럼 세션과 무관한 진짜 모델
  결함은 재로드해도 그대로 재현되므로, 이 방식이 진짜 결함을 감추지 않는다는 것도 확인된 사실로
  뒷받침된다. `app/providers/ollama.py`의 `unload_model()` + `app/services/job_posting_extraction.py`의
  `extract_job_posting_profile`(검증 실패 시 재시도)로 구현했고, 실제 Ollama 호출 테스트와
  가짜 provider 단위 테스트로 검증했다(`UNIT_TESTED`).
- `contracts/job-posting-extraction.md` 5절 `MODEL_RESPONSE_INVALID`의 `retryable: false`는
  이 완화책이 Python 내부에서만 처리되면(Java에 실패가 아예 안 넘어감) 그대로 유지해도 될 수
  있다 — Python이 내부 재시도까지 실패한 뒤에만 Java가 502를 받으므로, Java 쪽 재시도 정책은
  별개 논의로 남는다(확인 필요, Java·사용자 확인 없이 변경하지 않음).
- 표본 1회차(3-repeat) 실행만으로는 최종 모델 채택 근거로 부족하다 — 이력서 채택 때처럼 더 큰
  fixture 세트·반복 재평가가 필요.

## 채용공고 "담당 업무"(responsibilities) 필드 추가와 실제 회귀 (2026-08-03, 제안·코덱스 확인 필요)

**문제 제기**: 사용자 경험 임베딩(`user_profile_embedding.py`)은 README 서술형 텍스트를 쓰는데,
채용공고 임베딩(`job_posting_embedding.py`)은 직무명+기술명뿐이라 서술형 텍스트가 전혀 없는
비대칭이 있었다. 원문에 "담당 업무:" 문장이 있어도 `JobPostingExtraction` 스키마에 받을
필드가 없어 추출 단계에서 통째로 버려지고 있었다.

**대응**: `JobPostingResponsibility` 스키마와 `responsibilities` 필드를 추가하고, 프롬프트에
지시를 넣고, `build_job_posting_text`가 "담당 업무: ..." 섹션을 포함하도록 고쳤다.

**실제 검증에서 발견한 심각한 회귀**: `JobPostingExtraction`에 `responsibilities` 필드를
추가한 것만으로(프롬프트 문구와 무관하게, 필드를 스키마 어디에 둬도) qwen2.5의 `evidence`
배열 생성이 **통째로 비어버리는** 회귀가 실제 fixture(`backend_java_spring.txt`,
`game_server_developer.txt`)로 재현됐다. `jobTitle`·기술·담당 업무 **값 자체는 정확하게**
채워지는데 `evidenceIds`가 전부 빈 배열이라, `filter_unevidenced_candidates`를 거치면
**최종 결과가 통째로 빈 채용공고**가 된다(`jobTitle`까지 포함). 근거가 비어 있으면
`validate_evidence`도 에러를 안 던져서(위조도 유령 참조도 없으므로) 조용히 실패한다 — 방금
만든 재시도 로직도 이 케이스는 안 잡는다.

**2026-08-03 사용자 확인 — 대응 방향**: 매 요청 재로드·Ollama 설정 조정과 마찬가지로, 임시
패치보다 구조적으로 격리하는 방향을 택했다. **직무명·기술 추출과 담당 업무 추출을 완전히
분리된 두 호출로 나눴다**:

- `JobPostingCoreExtraction`(직무명·필수/우대 기술, `responsibilities` 없음) — 원래
  프롬프트·스키마를 그대로 복원. `OllamaProvider.extract_job_posting`이 이 스키마로 호출한다.
- `JobPostingResponsibilityExtraction`(담당 업무만) — 완전히 별도 프롬프트·스키마·호출.
  `OllamaProvider.extract_job_posting_responsibilities`.
- `job_posting_extraction.py`의 `extract_job_posting_profile`이 두 호출을 각각 독립적으로
  실행·검증·(실패 시) 재시도한 뒤 `_merge_core_and_responsibilities`로 합친다. 두 호출은
  독립된 LLM 요청이라 evidenceId가 우연히 겹칠 수 있어(둘 다 "e1"부터 시작하는 식), 담당
  업무 쪽에 `r_` 접두사를 붙여 재배정한다.

**검증 결과**: 분리 후 `JobPostingCoreExtraction`(직무명·기술) 쪽 회귀는 실제 fixture로
고쳐졌음을 확인했다(`evidence` 정상 생성). 그런데 **분리해도 `extract_job_posting_responsibilities`
자체는 evidence를 계속 안 채운다** — 완전히 격리된 좁은 스키마로도 4회 이상 반복 확인해
매번 재현됐다(우연한 flaky 아님, 값은 정확하지만 evidenceIds만 비움). 이건 스키마를 합친
게 원인이 아니라 **qwen2.5가 "담당 업무" 추출 자체에서 근거를 잘 안 만드는 모델 고유의
약점**으로 보인다(원인 미확인). 이 사실을 `tests/providers/test_ollama.py`의
`test_extract_job_posting_responsibilities_returns_evidence_linked_result`에 `xfail`로
명시해서 상태를 정직하게 남겨뒀다 — 모델이 개선되면 이 테스트가 예상외로 통과해서 알 수 있다.

**운영 안전성**: `filter_unevidenced_candidates`가 근거 없는 항목을 조용히 제거하므로,
이 한계는 서비스를 깨뜨리지 않는다 — 담당 업무가 그냥 빈 배열로 나오고, `build_job_posting_text`는
빈 섹션을 건너뛰어 기존(직무명+기술만) 임베딩 텍스트로 자연스럽게 돌아간다. 근거 없는 값을
지어내지 않는다는 원칙은 지켜지지만, **이 필드가 지금 후보 모델로는 실질적인 가치를 못 낸다.**

**검증 상태**: 스키마·병합·필터링·임베딩 텍스트 조합 로직은 `UNIT_TESTED`(가짜 provider,
네트워크 없음, 총 6건 신규). `JobPostingCoreExtraction` 회귀 수정은 실제 Ollama 호출로
검증(`UNIT_TESTED`). `JobPostingResponsibilityExtraction`은 실제 Ollama 호출 테스트가
`xfail`로 실패를 예상하는 상태 — **qwen2.5로는** 이 필드를 실사용 가능하다고 볼 수 없다.

**2026-08-03 추가 확인 — exaone3.5는 담당 업무 추출에서 정상 동작, 혼합 provider로 전환**:
qwen2.5 대신 exaone3.5로 담당 업무 전용 호출을 실제 fixture 3개(`backend_java_spring.txt`,
`game_server_developer.txt`, `llm_rag_backend.txt`) × 2회씩 테스트했더니 **6/6 전부 성공**했다
(값·근거 모두 정확). 사용자 확인 후 **직무명·기술(core)은 `OLLAMA_MODEL`(qwen2.5)을 그대로
쓰고, 담당 업무만 새 설정 `OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL`(exaone3.5)로 별도
호출**하는 혼합 provider 구조로 바꿨다.

- `extract_job_posting_profile(source_text, core_provider, responsibility_provider=None)` —
  두 번째 provider를 생략하면 첫 번째를 그대로 재사용(하위 호환).
- `app/providers/ollama_client.py`에 `get_ollama_job_posting_responsibility_provider` 추가,
  라우터(`app/job_postings/router.py`)가 두 provider를 모두 주입해 넘긴다.
- 실제 라우터까지 포함한 end-to-end 테스트(`test_extract_succeeds_with_real_ollama`)가
  두 모델을 함께 호출하는 이 경로로 통과했다(`UNIT_TESTED`).
- 표본이 3 fixture × 2회로 작아 `확인 필요` — 이력서 채택 때 수준(4개 자료 × 3모델)의
  재평가가 필요하다.
- **확인 필요(계약 영향)**: 계약 성공 응답의 `modelProvider`/`modelName`은 필드가 하나뿐인데
  이제 모델이 2개(core·responsibility) 쓰인다. 지금은 core 모델 이름만 응답에 담기고
  담당 업무 쪽 모델 정보는 응답에 드러나지 않는다 — Java·사용자 확인 필요.
- 이 fixture 테스트 중 core(qwen2.5) 쪽에서 이미 알던 세션 플레이키니스가 그대로 재현됐다
  (`backend_java_spring.txt`는 core가 조용히 비었고, `game_server_developer.txt`는 재시도까지
  실패). 오늘 담당 업무 작업과는 무관한, 기존에 문서화된 한계다.

**다음 단계(확인 필요)**

- 3 fixture × 2회보다 큰 표본으로 exaone3.5의 담당 업무 추출 재평가 필요
- 이 필드는 아직 `contracts/job-posting-extraction.md`에 없다 — 스키마 변경(계약 변경)이자
  이제 모델 2개를 쓰는 구조 변경이라 Java·사용자 확인 전까지 실사용 기준으로 쓰지 않는다
- "값은 정확한데 evidence만 빈 경우"를 `validate_evidence`가 놓친다는 것도 이번에 드러났다 —
  후보 항목이 있는데 evidence 배열 전체가 비어 있으면 재시도 대상으로 볼지는 별도 결정 필요
  (지금은 조용히 필터링만 됨)

## 채용공고 추출 Gemini 폴백 추가 (2026-08-04)

**평가 스크립트가 재시도 효과를 못 증명하던 문제부터 고쳤다**: `job_posting_model_comparison.py`가
`provider.extract_job_posting`을 직접 호출하고 자체 검증 로직을 썼을 뿐, 세션 오염 완화책
(`_extract_core_with_retry`, 2026-08-03)을 전혀 타지 않고 있었다. 이 스크립트를 실제 운영
경로(`_extract_core_with_retry`)를 그대로 재사용하도록 고치는 과정에서 별개의 버그도
발견했다 — `provider.extract_job_posting`이 이미 `JobPostingCoreExtraction`(담당 업무 없음)을
반환하도록 바뀌었는데(2026-08-03), 이 스크립트는 여전히 `filter_unevidenced_candidates`
(`JobPostingExtraction` 전용)에 그 결과를 그대로 넘기고 있어서 `AttributeError`로 즉시
깨지는 상태였다. 둘 다 고쳤다.

**고친 뒤 실제로 재평가한 결과, 예상과 반대되는 심각한 사실이 나왔다**:

| 모델 | 이전(2026-08-03) 통과율 | 이번(재시도 적용 후) 통과율 |
| --- | ---: | ---: |
| `qwen2.5:latest` | 86% | 57% |
| `exaone3.5:latest` | 67% | 57% |
| `llama3.2:latest` | 14% | 19% |

재시도가 27건 발동했는데 **단 한 건도 성공으로 복구되지 않았다**(qwen2.5 3건, exaone3.5 9건,
llama3.2 15건 — 전부 재시도해도 실패 유지). 이는 "재로드 직후 첫 요청 9/9 성공"이었던
2026-08-03 조사 결과와 정면으로 반대된다. 게다가 이전엔 멀쩡했던 fixture(`backend_java_spring.txt`,
`ai_ml_engineer.txt` 등)까지 이번엔 3개 모델 전부에서 동시에 실패해서, 오염이 개별 fixture
수준이 아니라 더 넓게 퍼진 것으로 보인다.

**Gemini로 교차 검증해서 원인을 코드가 아니라 Ollama 환경으로 좁혔다**: 방금 실패한 fixture
3개(`backend_java_spring.txt`, `ai_ml_engineer.txt`, `game_server_developer.txt`)를 같은
검증 코드(`validate_evidence`)로 Gemini에 그대로 태워봤더니 **3/3 전부 깨끗하게 성공했다**
(jobTitle·기술·evidence 전부 정확). 같은 코드가 Gemini에서는 통과하고 Ollama에서는
동시다발로 실패한다는 건, 저희 스키마·검증·필터링 로직의 버그가 아니라 **이 컴퓨터의
Ollama 실행 환경(오늘 하루 수백 번의 로드·언로드로 누적된 상태로 추정, 원인 미확정)에
국한된 문제**라는 뜻이다. `ollama serve` 프로세스 자체를 재시작해서 재현성을 다시
확인하는 건 아직 안 했다(확인 필요로 남김).

**대응(사용자 확인, 구현 완료)**: 원래 `docs/architecture/llm-providers.md`가 명시했던
설계 의도(`OllamaProvider`·`GeminiProvider`를 "나중에 `LlmGateway`에서 서로 바꿔 끼울 수
있게" 같은 인터페이스로 만듦 — 노션 "Python AI 분석 구현 문서" 16번 항목을 가리키는
한 줄짜리 언급뿐이었고, 이 repo에는 실제 구현·상세 설계가 없었다)를 실제로 채웠다.
**Ollama가 재시도까지 실패하면 Gemini로 폴백**하도록 `extract_job_posting_profile`을
고쳤다:

- 직무명·기술(core)과 담당 업무(responsibilities)는 각자 독립적으로 Ollama 재시도를 거친다.
- 둘 중 하나 또는 둘 다 실패하면 Gemini를 **한 번만** 호출해서(요청 수를 아끼기 위해)
  실패한 쪽을 채운다. Gemini 결과도 `validate_evidence`를 통과해야 한다.
- Gemini도 실패하면 Gemini의 예외가 아니라 **원래 Ollama 예외를 그대로 전달**한다 —
  라우터가 이미 처리하는 예외 타입(`OllamaUnavailableError`/`OllamaResponseError`/
  `JobPostingEvidenceValidationError`)을 그대로 유지해서 새 예외 종류를 추가로 처리할
  필요가 없게 했다.
- 채용공고는 공개 회사 정보라 개인정보 가드레일이 적용되지 않으므로(계약 서문), 이력서·
  희망 직무와 달리 Gemini 무료 등급 데이터 제한 정책 확인 없이 실사용 폴백으로 쓸 수
  있다고 판단했다(요청 제한으로 이따금 실패하는 건 별개 확인 필요).
- 계약 응답의 `modelProvider`/`modelName`이 실제로 core를 만든 provider를 반영하도록
  `extract_job_posting_profile`의 반환 타입을 `JobPostingExtractionResult`(추출 결과 +
  `core_provider_name`/`core_model_name`)로 바꿨다 — Gemini가 core를 대신 채웠는데
  응답에 "ollama"라고 남는 걸 막았다. 담당 업무 쪼가 어느 provider에서 왔는지는 계약이
  아직 모델 1개만 가정해서 응답에 안 드러난다(기존에 이미 확인 필요로 남긴 항목).

**구현 위치**: `app/providers/gemini_client.py`(신규, DI 팩토리), `app/services/job_posting_extraction.py`
(`extract_job_posting_profile`에 `fallback_provider` 매개변수 추가), `app/job_postings/router.py`
(Gemini provider 주입).

**검증**: 가짜 provider 단위 테스트 3건(Gemini 성공 복구/Gemini도 실패/폴백 없음) +
실제 라우터 end-to-end 테스트 1건(Ollama를 실제로 끊고 Gemini가 실제로 대신 성공하는지
확인, mock 아님) 신규. 전체 테스트 150개 통과(1 xfail은 기존에 알던 것).

**다음 단계(확인 필요)**

- `ollama serve` 프로세스 자체를 재시작한 뒤 같은 fixture로 재평가해서, 오늘 통과율 하락이
  정말 서버 프로세스 누적 상태 때문인지 확정
- Gemini 무료 등급 요청 제한으로 폴백까지 실패하는 빈도를 실제 트래픽으로 확인 필요
- 계약(`job-posting-extraction.md`)의 `modelProvider`가 "gemini"일 수 있다는 걸 문서에
  반영할지, 담당 업무 쪼 provider 정보를 응답에 추가할지는 Java·사용자 확인 필요

## 기술 태그 정규화 (2026-07-31 사용자 확인)

자기소개(수기 입력·이력서)에 적힌 표현은 추상적이고, 채용공고에 적힌 표현은 구체적이라 그냥
자유 텍스트로 비교하면 정확하지 않다. 고정 태그 목록을 두고 그 목록 기준으로 비교하기로 했다.

- **고정 태그 목록 출처**: Java가 프로젝트 자체 기본 태그 30개와 별칭을 관리한다. 다른 취업
  사이트의 태그 목록을 복사하지 않는다. 채용공고 구조화 추출(`contracts/job-posting-extraction.md`)이
  새 `rawName`을 발견하면 표준 태그로 자동 등록하지 않고 후보로 저장한다. 사용자 또는 관리
  정책의 확인을 받은 뒤에만 표준 태그로 승격한다.
- **사용자 커스텀 태그**: 기본 목록에 없는 기술은 사용자가 원문 그대로 추가할 수 있다. 커스텀
  태그는 사용자 입력 출처로 보존하며 기존 표준 태그를 자동으로 덮어쓰거나 합치지 않는다.
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
근거 추출, 수기 입력 분리, 검색어 생성), 사용자 경험 임베딩(README+검증된 기술), 채용공고
임베딩+의미 유사도 계산, 채용공고 재정렬은 구현·단위 테스트를 마쳐 위 검증 상태 표로 옮겼다 —
Java API 라우트 연결 전까지는 완료로 보지 않는다.

2026-07-31 확인: 이력서 PDF가 없어 "사용자 경험" 서술형 텍스트가 없던 문제는, 저장소 README를
새로 조회해서(`app/services/repository_readme.py`) 검증된 기술 목록과 합쳐 임베딩하는 것으로
해결했다(`app/services/user_profile_embedding.py`). 이어서 채용공고도 같은 방식(직무명+기술
목록)으로 임베딩(`app/services/job_posting_embedding.py`)해, 실제 Gemini 임베딩으로 "사용자
경험 vs 채용공고" 유사도 비교를 처음 end-to-end로 검증했다 — 관련 있는 공고(백엔드, 0.821)가
무관한 공고(프론트엔드, 0.627)보다 뚜렷이 높게 나왔다(둘 다 예시 데이터, 표본 1건).

2026-08-01 확인: 실제 채용공고 5건(백엔드 Java/Spring, 백엔드 Python/FastAPI, 게임 서버 Java,
프론트엔드, 카페 매니저)으로 재정렬(`app/services/job_posting_ranking.py`)을 검증했다. 새
로직 없이 기존 `rerank_candidates`만으로 관련도 순서(0.821 > 0.706 > 0.671)가 맞게 나왔고,
무관한 공고 2건은 최소 유사도(0.65, 이번 예시 기준) 미달로 정확히 제외됐다.

2026-08-01 확인: "부족 기술과 추천 이유 생성"은 LLM으로 자연어 문장을 짓지 않고, 결정론적
구조화 데이터(일치 여부·유사도 점수)만 반환하기로 했다(`app/services/job_fit_summary.py`).
문장으로 꾸미는 건 프론트엔드·Java 몫이다 — 할루시네이션 위험이 전혀 없고, `app.js`의
placeholder가 이미 체크리스트 형태로 표현하도록 되어 있어 그대로 맞는다.

2026-08-01 확인: "근거 없는 기술·경력 제거"는 오늘 세션 이전부터 이미 구현·테스트돼 있었다
(`job_posting_extraction.py`, `resume_extraction.py`의 `validate_evidence`·
`filter_unevidenced_candidates`) — 새로 만들 것 없이 검증 상태 표로 옮겼다.

2026-08-01 확인·2026-08-01 리뷰 반영: "모델 성능·토큰·단계별 처리시간 측정" 항목은
**"Python 개별 호출부 처리시간 기초 계측"**으로 이름을 정정했다 — 실제로 재는 건 소요
시간뿐이고 정확도·토큰·분류 성능은 다루지 않는다. 시간만(범용 타이머), 로그로만 남기기로
범위를 확정했다(`app/services/performance_tracking.py`) — 기존 함수 시그니처는 그대로 두고
호출부를 `measure_stage`로 감싸는 방식. 계약이 요구하는 전체 구간 중 지금은 일부(모델
호출·GitHub 조회)만 계측하며, 나머지(PDF 추출·개인정보 제거 시간, Python API 전체 처리
시간, Java–Python 왕복 시간, 성공/실패 구분, 요청 식별자)는 위 검증 상태 표에 남은 항목으로
명시했다.

**Python 10개 범위 축소 목록을 전부 최소 구현 또는 검증 상태 표로 옮겼다.** 실제 GitHub
데이터와 Java API를 사용한 통합 검증은 아직 남아 있다(위 각 행의 `NOT_TESTED`/`NOT_IMPLEMENTED`
항목 참고) — "완료"가 아니라 "각 항목이 UNIT_TESTED 이상에 도달했고 다음 단계가 정리됨"으로
읽어야 한다. 남은 결정 사항은 아래 계약 확정 하나뿐이며, 이건 Python이 임의로 결정할 수
없고 사용자·Codex 확인이 필요하다.

| 우선순위 | 작업 | 계약 상태 | 시작 조건 |
| ---: | --- | --- | --- |
| 1 | 채용공고 구조화 추출 API 확정 | `contracts/job-posting-extraction.md` 제안 | 계약 MVP 확정. `contracts/job-search-tool.md`가 넘기는 `jobPostings[].sourceText`를 그대로 입력으로 쓸 수 있는지 확인. 텍스트 최대 길이 공유 여부, `jobTitle` 미채움 시 처리 방식 결정 필요 |

**확인 필요 — Gemini에 실제 사용자 값 전달**: `generate_job_search_keyword_suggestions`는 Gemini
무료 등급 데이터 제한 정책 적용 대상이다. 희망 직무·기술명이 이력서 원문만큼 민감하지는 않지만,
실제 사용자 값을 Gemini에 보내도 되는지 아직 확인되지 않았다. 확정 전까지는 `OllamaProvider`만
실제 서비스에 연결하고, Gemini는 모델 비교·인터페이스 검증 용도로만 쓴다.

Python 모델 이름은 현재 연동 검증용 임시값이며 실제 채택 모델은 확인이 필요하다.

**2026-08-03 해결 — 이력서 PDF 파이프라인 처리**: PR #41(병합됨)로 확정됐다. Java가
`document-extraction.md`를 **폐기**로 표시하고 `DocumentController` 등 PDF 업로드 관련
Java 코드를 삭제했다 — MVP는 PDF·이력서·포트폴리오 입력 없이 기술 태그+공개 GitHub
저장소만으로 간다. `contracts/document-extraction.md`는 이력 확인용으로만 남는다.

**2026-08-03 Python 쪽도 정리 완료(사용자 확인)**: `app/documents/`,
`app/services/pdf_extraction.py`, `app/services/resume_extraction.py`,
`app/guardrails/personal_information_sanitizer.py`, `app/schemas/profile_candidate.py`,
`app/schemas/document.py`, `evaluation/model_comparison.py`, `tests/fixtures/resumes/`와
관련 테스트 전부를 삭제했다. `OllamaProvider`/`GeminiProvider`의 `extract_resume_profile`,
`ollama_resume_model` 설정, `main.py`의 documents 라우터 등록도 함께 제거했다(공유 파일은
삭제 대신 해당 부분만 편집). 삭제 전 별도 조사로 교차 참조를 전부 확인했고, 삭제 후 전체
테스트 146개 통과(1 xfail은 기존에 알던 것)로 회귀 없음을 확인했다.

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
| 사용자 프로젝트 출처 목록 API(`GET /api/v1/project-sources`) | `UNIT_TESTED` | Service·Controller 테스트 + PostgreSQL/Testcontainers 통합 테스트 통과(PR #31) | Python이 저장소 근거 추출(1번) 대상 저장소를 고를 때 이 목록을 실제로 쓰도록 연결 |
| 표준 기술 태그 검색 API(`GET /api/v1/technology-tags`) | `UNIT_TESTED` | Controller·정규화 단위 테스트 + Testcontainers 통합 테스트, Java 전체 테스트 107개 통과(PR #33) | Python `match_skill_tag`가 이 API의 고정 태그 목록(과 캐시된 임베딩)을 받아 쓰도록 연결 — 아직 Java–Python 계약 없음. 채용공고에서 발견한 새 `rawName`은 후보로만 저장되고 자동 표준 등록되지 않음(사용자·관리 확인 후 승격) |

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
