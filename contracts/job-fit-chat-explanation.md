# 적합도 결과 설명 채팅 계약

상태: **제안 — 아이디어 단계, 코덱스·사용자 확인 필요. 아직 구현 없음.**

## 0. 왜 이 문서가 필요한가

`app/services/job_fit_summary.py`는 2026-08-01에 **"LLM으로 자연어 문장을 짓지 않고 결정론적
구조화 데이터만 반환한다"**고 확정했다(`docs/current-work.md` 참고) — 할루시네이션 위험을
원천적으로 없애기 위해서였다. 그런데 사용자가 자신의 적합도 결과(차트화된 `JobFitSummary`)를
보다가 "왜 이 기술은 안 맞다고 나와?"처럼 자유롭게 물어보는 채팅을 추가하면, 다시 LLM이
자유 문장을 생성하게 된다.

이 문서는 그 예외를 **완전히 열어두는 대신, 좁게 막힌 형태로만** 허용하는 설계다 —
채팅이되 자유 대화가 아니라, **이미 계산된 구조화 데이터 밖으로 못 나가는 채팅**을
목표로 한다. 아래 가드레일(4절)이 이 문서의 핵심이다.

## 1. 실행 경계

1. Java가 이미 확정된 `JobFitSummary`(또는 대응하는 저장 모델)를 조회해서 이 계약의
   Python API에 함께 전달한다 — Python은 DB를 직접 조회하지 않는다.
2. 사용자의 채팅 메시지 1턴 = Python 내부 API 호출 1번(동기 HTTP). Python은 대화 상태를
   저장하지 않는다 — Java가 대화 이력을 소유하고, 매 요청마다 필요한 만큼만 함께 보낸다.
3. Python은 응답 문장을 만들고, 그 문장이 `JobFitSummary`의 값과 실제로 맞는지 사후
   검증한 뒤 돌려준다(4절 근거 검증 참고). 검증에 실패하면 문장 없이 에러를 반환한다 —
   틀린 문장을 사용자에게 보여주지 않는다.
4. Java가 채팅 메시지·응답을 저장하고 사용자에게 렌더링한다.

## 2. 책임 경계

### Java

- 사용자 인증, 대화 세션 생성·조회·만료 관리.
- `JobFitSummary` 등 근거 데이터를 조회해 Python에 전달.
- 대화 이력 중 이번 턴에 필요한 만큼만(3절 참고) 골라 보낸다 — 전체 이력을 무조건 다 보내지 않는다.
- Python이 거절(`refused: true`)을 반환하면 사용자에게 "이 화면에서 답할 수 있는 질문이 아니다"류 안내를 보여준다.

### Python

- 제공된 구조화 데이터(`JobFitSummary`)와 직전 대화 몇 턴만 근거로 답한다. 그 밖의 지식(일반
  커리어 상담, 다른 채용공고, 시황 등)으로 답하지 않는다 — 스코프 밖이면 거절한다.
- 응답에서 언급한 수치·기술명이 실제로 입력받은 `JobFitSummary`와 일치하는지 검증한다
  (`validate_evidence`류 원칙의 확장 — 아래 4절).
- 원문 이력서·채용공고 전체 텍스트, 개인정보는 절대 모델 프롬프트에 넣지 않는다 — 구조화된
  요약 값만 넣는다.

## 3. 내부 API (제안)

```http
POST /internal/v1/job-fit/chat
Content-Type: application/json
X-Internal-Token: {shared-secret}
X-Request-Id: {uuid}
```

### 요청 본문

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---:|---|
| `chatSessionId` | UUID 문자열 | 예 | Java가 생성한 대화 세션 식별자(로그·계측용, Python은 상태 저장 안 함) |
| `jobFitSummary` | `JobFitSummary` | 예 | `job_fit_summary.py`의 기존 구조화 결과. 이 값 밖의 정보는 답변에 못 씀 |
| `recentTurns` | `ChatTurn[]` | 아니오 | 직전 대화 중 Java가 고른 최근 N턴만(확인 필요: N값). 생략하면 첫 턴으로 간주 |
| `userMessage` | string | 예 | 공백 아님, 최대 길이 이하(확인 필요: 값) |

`ChatTurn`: `{ "role": "user" | "assistant", "content": string }`

### 성공 응답

```json
{
  "requestId": "41a89594-09f8-45ca-a558-3f4e84ca838e",
  "data": {
    "chatSessionId": "...",
    "assistantMessage": "Spring Boot 경험은 확인됐지만, Kafka는 보유 기술 목록에 없어서 우대 조건 중 하나가 비어 있어요.",
    "refused": false,
    "referencedSkillNames": ["Spring Boot", "Kafka"],
    "modelProvider": "ollama",
    "modelName": "configured-model-name"
  },
  "error": null,
  "timestamp": "2026-08-03T08:00:00Z"
}
```

- `referencedSkillNames`: 응답 문장이 실제로 언급한 기술명 목록 — Python이 사후 검증에 쓰고,
  Java·프론트가 밑줄/링크 처리 등에 재사용할 수 있다.
- `refused`가 `true`면 `assistantMessage`는 정해진 거절 문구 중 하나이고(자유 생성 아님),
  `referencedSkillNames`는 빈 배열이다.

## 4. 가드레일 (이 계약의 핵심)

기존 채용공고·이력서 추출의 "근거 없는 값은 만들지 않는다" 원칙을 자유 대화 형태로
확장한 것이다. 다섯 가지를 반드시 지킨다.

1. **데이터 근거 제한**: 시스템 프롬프트가 `jobFitSummary`에 있는 값만 근거로 쓰라고
   강제한다. 원문 이력서·채용공고 전체 텍스트는 프롬프트에 절대 넣지 않는다(개인정보
   노출 방지 + 토큰 절약 동시 달성).
2. **응답 길이 상한**: 프롬프트 지시("N문장 이내") + 모델 요청의 `num_predict`/`max_tokens`
   같은 하드 제한을 같이 건다. 길이가 늘어나면 맥락 이탈 확률도 토큰 비용도 같이 올라간다는
   문제 제기를 그대로 반영한다.
3. **대화 이력 제한**: Python은 상태가 없다 — 매 요청 `recentTurns`로 받은 것만 쓰고, 그
   턴 수는 Java가 정한 상한을 넘지 않는다(확인 필요: 정확한 값). 대화가 길어져도 매번 전체
   이력을 다시 보내 토큰이 누적 폭증하는 걸 막는다.
4. **스코프 밖 질문 거절**: "일반 커리어 상담", "다른 채용공고 정보", "이 데이터에 없는
   수치"류 질문은 정해진 거절 문구로만 답하도록 지시한다. 거절 여부(`refused`)를 구조화
   필드로 분리해서 Java·프론트가 임의 문장 파싱 없이 처리할 수 있게 한다.
5. **사후 근거 검증**: 응답에서 언급한 기술명·수치가 `jobFitSummary`에 실제로 존재하는지
   검증한다(`validate_evidence`와 같은 원칙 — 근거 없는 문장을 그대로 사용자에게 보여주지
   않는다). 검증 실패 시 5절의 `MODEL_RESPONSE_INVALID`로 처리한다.

## 5. 실패 응답

`document-extraction.md` 6절과 같은 봉투 형식이다.

| HTTP | `errorType` | 원인 | `retryable` |
|---:|---|---|---:|
| 422 | `INTERNAL_TOKEN_REQUIRED` | `X-Internal-Token` 누락 | false |
| 401 | `INTERNAL_UNAUTHORIZED` | 내부 토큰 불일치 | false |
| 422 | `INVALID_CHAT_REQUEST` | UUID 형식 오류, 빈 메시지, 최대 길이 초과 | false |
| 503 | `MODEL_UNAVAILABLE` | `OllamaUnavailableError`, `GeminiUnavailableError` | true |
| 502 | `MODEL_RESPONSE_INVALID` | 응답이 근거 검증(4절 5번)을 통과하지 못함 | 확인 필요 — 오늘 채용공고 조사에서 이런 실패 일부가 세션 상태 때문일 수 있다는 게 확인됨(`docs/current-work.md` 참고) |

## 6. 확인 필요 (구현 전 결정 필요, 아이디어 단계라 특히 많음)

- 이 기능을 정말 만들지부터가 확인 필요 — `job_fit_summary.py`의 "LLM 없음" 원칙에 대한
  명시적 예외라, 사용자·코덱스가 이 문서 자체를 승인해야 다음 단계로 간다.
- `recentTurns` 최대 개수, `userMessage` 최대 길이, 세션 만료 정책 — 전부 확인 필요.
- LLM provider 선택(Ollama vs Gemini) — 실시간 채팅이라 응답 속도가 중요한데, 로컬 Ollama의
  지연시간(오늘 채용공고 평가에서 모델당 20~60초)이 채팅에 맞는지 확인 필요. 채용공고
  추출과 다른 성능 요구 조건일 수 있다.
- 사후 근거 검증 실패 시 재시도할지 그냥 에러로 끝낼지 — 채용공고 쪽 재시도 정책(2026-08-03
  결정)과 같은 걸 쓸지 별도로 정할지 확인 필요.
- `referencedSkillNames`가 실제로 프론트에 필요한 형태인지, 아니면 다른 방식(강조할 문장
  구간 등)이 나을지 확인 필요 — 아직 프론트 요구사항이 없다.
- 이 채팅이 몇 개 화면(적합도 결과 화면만? 다른 분석 결과도?)에 붙을지 범위가 아직 없다.
