# Python AI 서버

Python 서버는 채용공고 구조화 추출, 저장소 근거 기반 기술 프로필, 임베딩·의미 유사도·후보
재정렬과 Ollama·Gemini 모델 호출을 담당한다. 사용자 인증, 작업 상태와 확정 데이터는 저장하지
않으며 Java가 소유한다. (2026-08-03: PDF·이력서·포트폴리오 입력 파이프라인은 MVP에서
제거됐다 — `docs/current-work.md` 참고.)

이 문서의 구현 상태는 원격 `python` 브랜치를 기준으로 한다. 아직 실제 API 라우트로 연결되지 않은 기능을 완료된 API로 표시하지 않는다.

## 구현 API 목록

| 영역 | 기능 | Method | Endpoint | 성공 상태 |
|---|---|---|---|---:|
| 운영 | 상태 확인 | `GET` | `/internal/v1/health` | 200 |
| 채용공고 | 채용공고 텍스트 구조화 (제안 — 계약 미확정) | `POST` | `/internal/v1/job-postings/extract` | 200 |

모든 `/internal/v1/*` 요청은 `X-Internal-Token` 헤더를 요구한다 (아래 "내부 서비스 인증" 참고).

### 상태 확인

```http
GET /internal/v1/health
X-Internal-Token: {shared-secret}
```

```json
{
  "status": "UP",
  "model_ready": false
}
```

### 채용공고 추출 (제안 — 코덱스 확인 필요)

계약: [채용공고 구조화 추출 계약](../contracts/job-posting-extraction.md), 상태 "제안". JSON 본문(`jobPostingId`, `extractionTaskId`, `sourceText`)을 받는다. 채용공고는 공개 정보라 개인정보 제거 단계가 없다 — 바로 Ollama 구조화 추출(`OLLAMA_MODEL`)로 간다.

- 근거 검증·필터링(할루시네이션·중복·유령 참조 차단, 근거 없는 후보 제거)은 `app/services/job_posting_extraction.py`.
- 실제 검증: `tests/job_postings/test_job_postings_extract.py` — 실제 Ollama 호출(mock 아님).
- `qwen2.5:latest`로는 `requiredSkills`·`evidence`는 안정적으로 채우지만 `jobTitle`은 채우지 않는 경우가 실제로 있었다(계약 문서 8절 참고) — 아직 모델 평가·프롬프트 튜닝을 안 했다.
- **Gemini 폴백(2026-08-04, PR #45 리뷰로 2026-08-04 수정)**: Ollama가 근거 검증 재시도까지
  실패하면 Gemini로 폴백한다. `GEMINI_API_KEY` 등이 없어도 Ollama만으로 정상 동작하며(설정
  부재는 예외가 아니라 폴백 미사용으로 처리), Gemini 클라이언트는 Ollama가 실제로 실패했을
  때만 지연 생성한다. Gemini는 외부 서비스라 "공개 정보라 정책 확인 불필요"하다고 임의로
  판단하지 않고, 대신 전송 직전 이메일·전화번호를 제거한다(정규식 커버리지는 확인 필요).
  응답의 `modelExecutions` 배열이 `CORE_EXTRACTION`/`RESPONSIBILITY_EXTRACTION` 단계별로
  실제 처리한 provider·모델을 정직하게 남긴다(`docs/current-work.md` 참고).

## 내부 서비스 인증 (2차 방어선)

네트워크 격리(Python 포트를 외부에 노출하지 않는 것)가 1차 방어선이고, 이 토큰 검증은 그게 뚫렸을 때를 대비한 2차 방어선이다. 최종 사용자 인증(Java GitHub OAuth)과는 무관한 별개의 값이다.

- `app/guardrails/internal_auth.py`의 `verify_internal_token`이 `/internal/v1/*` 라우터 전체에 적용된다.
- `X-Internal-Token` 헤더가 없으면 `422`, 있지만 설정값과 다르면 `401`을 반환한다.
- 비교는 타이밍 공격을 막기 위해 `hmac.compare_digest`를 사용한다.
- 환경변수 `INTERNAL_SERVICE_TOKEN`으로 주입하며, Java와 반드시 같은 값을 공유해야 한다. 발급·서명·만료가 있는 JWT가 아니라 양쪽이 미리 공유하는 고정 비밀값이다.

## 내부 구현 완료 — 아직 API로 연결되지 않음

다음은 실제 동작을 검증(단위 테스트, 일부는 실제 Ollama·Gemini 호출)했지만, 아직 HTTP 라우트로 노출되지 않고 서비스·제공자 계층 코드로만 존재한다.

| 영역 | 기능 | 위치 |
|---|---|---|
| 모델 제공자 | Ollama·Gemini 구조화 추출 (채용공고 예시) | `app/providers/ollama.py`, `app/providers/gemini.py` |
| 모델 제공자 | Ollama·Gemini 임베딩 생성 | `app/providers/embedding.py` |
| 서비스 | 코사인 유사도 계산 | `app/services/similarity.py` |
| 서비스 | 후보 재정렬(최소 유사도 필터·동점 처리) | `app/services/reranking.py` |

채용공고-확정 프로필 비교 실행 API가 이 기능들을 실제 요청에 연결하는 다음 작업이다.

## `ai-python` 구조

```text
app/
├─ health/        상태 확인 API
├─ guardrails/    내부 서비스 인증, 입출력 검증 (독립 계층이 아니라 경계 정책)
├─ providers/     Ollama·Gemini 모델 호출, 임베딩 생성
├─ schemas/       Java-Python 요청·응답과 모델 출력 스키마
└─ services/      채용공고 구조화 추출, 유사도 계산, 후보 재정렬 (LLM을 직접 호출하지 않는 로직 포함)
```

계층 이름의 근거는 [docs/architecture/layer-terminology.md](../docs/architecture/layer-terminology.md)를 따른다.

## 현재 미구현

- 채용공고-확정 프로필 비교 실행 API (채용공고 구조화 자체는 됨 — 임베딩·유사도·재정렬을 실제 비교 요청에 연결하는 부분이 남음)
- 오차 범위 계산·보정 (충분한 검증 데이터가 쌓이기 전까지는 설계만 존재, [docs/architecture/error-calibration.md](../docs/architecture/error-calibration.md) 참고)

## 필수 환경변수

`app/config.py`, `app/guardrails/settings.py`, `app/providers/settings.py`가 `pydantic-settings`로 읽는다. **필수값이 하나라도 없으면 서버 시작 자체가 실패한다**(`ValidationError`, 기본값 없는 필드는 생략 불가).

| 변수 | 용도 | 확인 상태 |
|---|---|---|
| `INTERNAL_SERVICE_TOKEN` | Java-Python 내부 인증 공유 비밀값 | 확인 필요 — 실제 채택 값 |
| `OLLAMA_MODEL` | 채용공고 직무명·기술 구조화 추출용 Ollama 모델 | 확인 필요 — 임시값(`qwen2.5:latest`) |
| `OLLAMA_JOB_POSTING_RESPONSIBILITY_MODEL` | 채용공고 담당 업무 추출 전용 Ollama 모델 | 확인 필요 — 임시값(`exaone3.5:latest`), qwen2.5는 이 호출에서 evidence를 못 채워서 분리(`docs/current-work.md` 참고) |
| `OLLAMA_EMBEDDING_MODEL` | Ollama 임베딩 모델 | 확인 필요 |
| `GEMINI_API_KEY`, `GEMINI_MODEL` | Gemini 연동. `GEMINI_MODEL`은 채용공고 추출의 Ollama 폴백으로 쓰인다(2026-08-04). **선택값** — 없으면 폴백 없이 Ollama만으로 동작한다(2026-08-04 PR #45 리뷰 반영, 이 셋만 예외적으로 필수가 아니다) | 확인 필요 |
| `GEMINI_EMBEDDING_MODEL` | Gemini 임베딩 모델 | 확인 필요 |
| `JOB_POSTING_EXTRACTION_MAX_TEXT_LENGTH` | 채용공고 텍스트 최대 길이(문자 수) | 확인 필요 — 계약이 제안 상태라 임시값, Java 설정과 맞춰야 함 |

실제 배포 환경(Linux)에 이 값들이 실제로 주입되는지는 배포 담당이 별도로 확인해야 한다(이 문서 작성 시점에는 로컬 `.env`만 확인함, 실제 비밀값은 커밋하지 않음 — `.env.example` 참고).

## 확인 필요

- 실제 채택할 Ollama(채용공고)·Gemini·임베딩 모델 이름 (지금 `.env`의 값은 전부 연동 코드 검증용 임시값)

## 구현 근거

- [Ollama API 문서](https://docs.ollama.com/)
- [Google Gen AI SDK 문서](https://googleapis.github.io/python-genai/)
