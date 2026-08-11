# 적합도 의미 유사도 — Python LLM-as-judge 설계 (제안)

상태: **제안 — 코덱스 API 작업과 취합해서 서로 리뷰 필요. 구현 전.**

## 0. 배경과 스코프

`docs/architecture/embedding-similarity.md`/`reranking.md`가 이미 정한 원칙대로, 조건
일치율(Java, 결정론적 규칙)과 의미 유사도(Python)는 서로 다른 값이며 하나의 불투명한
총점으로 합치지 않는다. 이 문서는 그중 **Python이 담당하는 의미 유사도를 무엇으로 어떻게
낼지**를 다룬다.

두 가지 방법을 검토했다.

1. **임베딩 + 코사인 유사도** — `embedding-similarity.md`의 실험(2026-07-28)에서 로컬
   Ollama 임베딩(`nomic-embed-text`)은 도메인 구분을 못 했고, Gemini 임베딩만 정확했다.
   그런데 Gemini는 오늘(2026-08-11) 무료 등급 일일 한도(RPD)를 이미 소진해서 태평양
   표준시 자정(한국 시간 오후 4~5시) 전까지 재검증이 불가능하다(실제 확인, 429
   RESOURCE_EXHAUSTED).
2. **LLM-as-judge** — 이미 검증된 추출 인프라(Ollama, 근거 ID 검증)를 재사용해 LLM이
   직접 적합도를 판단한다. Ollama는 로컬이라 할당량 문제가 없다.

**오늘은 Gemini 할당량이 없어서 2번(LLM-as-judge)으로 진행하기로 확정했다.**

### 반드시 지켜야 하는 제약 — 이미 확정된 원칙

`app/services/job_fit_summary.py`와 `app/schemas/job_fit_summary.py`에 2026-08-01
사용자 확인으로 이미 이렇게 못박혀 있다:

> LLM으로 자연어 추천 문장을 짓지 않는다. 일치 여부와 유사도 점수만 반환하고, 이걸
> 문장으로 꾸미는 건 프론트엔드·Java의 몫이다.

즉 이번 LLM-as-judge 설계도 **자유 문장을 만들면 안 된다** — 채용공고 추출(`job_posting_extraction.py`)과 같은 방식으로, LLM이 **구조화된 값 + 근거 ID**만 반환하고
Java·프론트가 문장으로 조립한다. 자연어 설명 자체가 필요하면 그건 이 문서 범위가
아니라 별도로 이미 제안돼 있는 [적합도 결과 설명 채팅](../../contracts/job-fit-chat-explanation.md)(아이디어 단계, 사후 근거 검증 포함) 쪽으로 가야 한다 — 이 두 기능을
섞지 않는다.

## 1. 이미 있는 것 (재사용 대상)

| 파일 | 상태 | 비고 |
|---|---|---|
| `app/schemas/job_fit_summary.py` (`JobFitSummary`, `SkillFit`) | 스키마 존재, API 미노출 | `similarity: float \| None`(범위 -1~1) 필드가 이미 예약돼 있음 — 이번 작업이 채울 자리 |
| `app/services/job_fit_summary.py` (`summarize_job_fit`) | 로직 존재, API 미노출 | 필수·우대 기술 각각의 `matched` 여부를 정확 문자열 일치로만 계산(동의어·오타 미처리, 확인 필요로 이미 명시됨) |
| `app/services/repository_evidence.py` (`analyze_repository`) | 로직 존재, API 미노출 | GitHub 저장소에서 기술 근거를 추출한다 — "사용자 기술 근거"의 출처 후보 |
| `app/services/technical_profile.py` (`merge_technical_evidence`) | 로직 존재, API 미노출 | 저장소 근거 + 수기 입력 근거를 `TechnicalEvidenceExtraction`(evidence + skills, evidence_ids 포함)로 병합 |
| `app/services/skill_tag_matching.py` | 로직 존재(다른 목적) | 기술 태그 하나를 고정 태그 목록과 임베딩으로 매칭 — 이번 작업과 다른 용도(기술 태그 정규화)지만 "1·2위 유사도 차이(margin)로 확신도 판단" 패턴은 참고할 만하다 |

**중요한 공백**: 이 표의 항목들은 전부 **로직만 있고 FastAPI 라우터에 연결돼 있지 않다**
(`app/main.py`는 `health`, `job_postings` 라우터만 등록). 즉 "사용자 기술 근거"를 Python이
받는 방법 자체가 아직 계약으로 없다 — 이건 이번 문서의 범위를 넘는 별도 확인 필요
항목이다(4절 참고).

## 2. 제안하는 흐름

```text
Java: 채용공고 추출 결과(이미 있음) + 사용자 기술 근거(TechnicalEvidenceExtraction, 확인 필요)
→ Python: 두 근거를 LLM에 함께 제공
→ LLM: 필수·우대 기술별로 "제공된 근거로 판단 가능한 적합도"를 구조화 값으로만 반환
→ Python: 응답의 evidence_ids가 실제로 입력 근거에 존재하는지 검증(validate_evidence와 같은 원칙)
→ Python: 검증 통과분만 JobFitSummary.similarity 및/또는 SkillFit 확장 필드로 반환
→ Java: 결정론적 규칙 판정과 분리해서 저장, 하나의 총점으로 합치지 않음
```

## 3. 출력 스키마 제안 (초안, 확인 필요)

`job_posting_extraction.py`가 이미 쓰는 "구조화 값 + evidenceIds" 패턴을 그대로 따른다.
자유 문장 필드를 두지 않는다.

```json
{
  "overallSimilarity": 0.62,
  "skillJudgments": [
    {
      "skillName": "Kubernetes",
      "required": false,
      "judgment": "SUPPORTED" ,
      "confidence": 0.8,
      "jobPostingEvidenceIds": ["e3"],
      "userEvidenceIds": ["u7", "u12"]
    }
  ]
}
```

- `judgment`: 자유 문자열이 아니라 고정 enum(예: `SUPPORTED` | `PARTIALLY_SUPPORTED` |
  `NOT_SUPPORTED` | `INSUFFICIENT_EVIDENCE`) — 정확한 값과 개수는 확인 필요.
- `overallSimilarity`는 기존 `JobFitSummary.similarity`(-1~1) 필드를 그대로 채운다.
- 근거 없이 `SUPPORTED`를 반환하면(= evidence_ids가 비어 있거나 실제 근거와 안 맞으면)
  `job_posting_extraction.py`의 `filter_unevidenced_candidates`와 같은 원칙으로 걸러낸다.

## 4. 확인 필요 (구현 전 결정 필요)

- **사용자 기술 근거를 Python이 어떻게 받는지 자체가 미정** — `repository_evidence.py`가
  API로 노출된 적이 없다. Java가 저장소 근거를 미리 계산해서 넘기는지, Python이 그때
  같이 계산하는지부터 정해야 한다. 이건 이 문서보다 먼저 풀어야 할 수도 있는 별도 계약.
- LLM provider 선택 — 오늘은 Ollama만 가능(Gemini 할당량 소진). qwen2.5/exaone3.5 중
  어느 쪽을 쓸지는 2026-08-11 fixture 평가 결과(exaone3.5가 core 추출 76% vs qwen2.5
  35%)를 참고하되, "적합도 판단"은 "채용공고 구조화 추출"과 다른 과제라 별도 평가 필요.
- `judgment` enum 값 종류와 `confidence` 범위·의미.
- 채용공고 필수·우대 기술 개수만큼 LLM 호출이 몇 번 필요한지(항목별 개별 호출 vs 한 번에
  전체 판단) — 호출 수가 늘면 지연시간·로컬 리소스 부담이 커진다.
- `overallSimilarity`를 개별 `skillJudgments`에서 어떻게 집계할지(평균? 필수 기술
  가중치?) — 아니면 LLM이 직접 하나의 값으로 판단할지.
- 사후 근거 검증 실패 시 재시도 정책(채용공고 추출과 같은 언로드+1회 재시도를 쓸지).
- Java-Python 계약 정식 문서화(`contracts/` 아래 새 파일, 이 문서가 승인된 뒤).

## 관련 문서

- [임베딩과 의미 유사도](embedding-similarity.md)
- [후보 재정렬](reranking.md)
- [적합도 결과 설명 채팅](../../contracts/job-fit-chat-explanation.md) — 자연어 설명이
  필요하면 이쪽 범위, 이 문서와 섞지 않음
- [공공기관 채용공고 분석 책임 경계](public-institution-job-analysis.md)
