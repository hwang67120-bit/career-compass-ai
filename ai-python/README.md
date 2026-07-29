# Python AI 서버

Python 서버는 PDF 문서에서 텍스트와 개인정보가 제거된 구조화 정보를 추출하고, 임베딩·의미 유사도·후보 재정렬과 Ollama·Gemini 모델 호출을 담당한다. 사용자 인증, 작업 상태와 확정 데이터는 저장하지 않으며 Java가 소유한다.

이 문서의 구현 상태는 원격 `python` 브랜치를 기준으로 한다. 아직 실제 API 라우트로 연결되지 않은 기능을 완료된 API로 표시하지 않는다.

## 구현 API 목록

| 영역 | 기능 | Method | Endpoint | 성공 상태 |
|---|---|---|---|---:|
| 운영 | 상태 확인 | `GET` | `/internal/v1/health` | 200 |

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
| 문서 처리 | PDF 페이지별 텍스트 추출 | `app/services/pdf_extraction.py` |
| 모델 제공자 | Ollama·Gemini 구조화 추출 (채용공고 예시) | `app/providers/ollama.py`, `app/providers/gemini.py` |
| 모델 제공자 | Ollama·Gemini 임베딩 생성 | `app/providers/embedding.py` |
| 서비스 | 코사인 유사도 계산 | `app/services/similarity.py` |
| 서비스 | 후보 재정렬(최소 유사도 필터·동점 처리) | `app/services/reranking.py` |

`POST /internal/v1/documents/extract`는 [PDF 문서 추출 계약](../contracts/document-extraction.md)에 MVP 확정됐으나, 실제 라우트·개인정보 제거 로직은 다음 작업이다.

## `ai-python` 구조

```text
app/
├─ health/        상태 확인 API
├─ guardrails/    내부 서비스 인증, 입출력 검증 (독립 계층이 아니라 경계 정책)
├─ providers/     Ollama·Gemini 모델 호출, 임베딩 생성
├─ schemas/       Java-Python 요청·응답과 모델 출력 스키마
└─ services/      PDF 텍스트 추출, 유사도 계산, 후보 재정렬 (LLM을 직접 호출하지 않는 로직 포함)
```

계층 이름의 근거는 [docs/architecture/layer-terminology.md](../docs/architecture/layer-terminology.md)를 따른다.

## 현재 미구현

- `POST /internal/v1/documents/extract` 실제 라우트 (multipart 수신, 계약 검증)
- 개인정보 제거(PII sanitization) — 텍스트 추출 직후, LLM 호출 전 단계
- 이력서 구조화 추출 스키마·프롬프트 (`JobPostingExtraction`과 동일한 패턴, 이력서용)
- 채용공고-확정 프로필 비교 실행 API (임베딩·유사도·재정렬을 실제 요청에 연결)
- 오차 범위 계산·보정 (충분한 검증 데이터가 쌓이기 전까지는 설계만 존재, [docs/architecture/error-calibration.md](../docs/architecture/error-calibration.md) 참고)

## 확인 필요

- 실제 채택할 Ollama·Gemini·임베딩 모델 이름 (지금 `.env`의 값은 전부 연동 코드 검증용 임시값)
- PDF 전송 방식과 후보 스키마 세부 필드는 계약 문서 기준, 구현 시 재확인

## 구현 근거

- [FastAPI Request Files](https://fastapi.tiangolo.com/tutorial/request-files/)
- [pypdf 공식 문서](https://pypdf.readthedocs.io/)
- [Ollama API 문서](https://docs.ollama.com/)
- [Google Gen AI SDK 문서](https://googleapis.github.io/python-genai/)
