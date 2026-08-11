# 채용 적합도 의미 비교 방식

상태: 제안 — 후보 평가 후 구현 방식을 확정한다.

## 결정된 책임 경계

- Java는 필수·우대 기술 등 명확한 조건을 규칙으로 판정한다.
- Python은 공고 담당 업무와 사용자 프로젝트 업무의 의미 관계만 비교한다.
- Python은 사용자의 기술 보유 여부와 지원 가능 여부를 판정하지 않는다.
- 조건 판정과 의미 비교를 하나의 불투명한 `overallSimilarity`로 합치지 않는다.
- LLM을 사용해도 자유 추천 문장, 근거 없는 점수와 보정되지 않은 confidence를 만들지 않는다.

상세 경계는 [내부 계약](../../contracts/job-evidence-similarity.md)을 따른다.

## 후보 A: 임베딩과 코사인 유사도

`nomic-embed-text`는 2026-07-28 실험과 2026-08-11
`ai-python/evaluation/job_evidence_similarity_spike.py` 재검증에서 업무 차이를 구분하지 못했다.

| fixture | 결과 |
|---|---:|
| 같은 백엔드 업무 | 0.9948 |
| 관련 없는 프론트엔드 업무 | 0.9980 |
| Python–Java | 1.0000 |
| Python–React | 1.0000 |

따라서 현재 모델·계산법은 운영 기본값에서 제외한다. Gemini 임베딩은 같은 fixture로
재평가해야 하며 일시적 할당량 소진을 아키텍처 결정으로 바꾸지 않는다.

## 후보 B: LLM-as-judge

LLM은 업무 관련 여부를 구조화 enum과 입력에 존재하는 근거 식별자로만 반환한다.
추출 성능이 의미 비교 성능을 보장하지 않으므로 별도 평가가 필요하다.

허용: `RELATED`/`NOT_RELATED`, 공고·프로젝트 근거 ID.
제외: 기술 `SUPPORTED` 판정, `overallSimilarity`, 보정되지 않은 confidence, 자유 추천 문장.

## 공통 품질 게이트와 순서

1. 같은 fixture로 Gemini 임베딩과 Ollama LLM-as-judge를 비교한다.
2. 업무 구분력, 반복 안정성, 근거 타당성, 지연시간과 자원 사용량을 측정한다.
3. 통과한 method, provider, model과 출력 척도를 계약에서 확정한다.
4. Python endpoint와 Java client·저장을 구현한다.
5. 계약 테스트, 실제 연결 테스트와 브라우저 테스트를 수행한다.

## 관련 문서

- [채용공고 근거 의미 비교 내부 계약](../../contracts/job-evidence-similarity.md)
- [임베딩과 의미 유사도](embedding-similarity.md)
- [후보 재정렬](reranking.md)
- [공공기관 채용공고 분석 책임 경계](public-institution-job-analysis.md)
