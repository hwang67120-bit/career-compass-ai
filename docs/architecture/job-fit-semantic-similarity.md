# 채용 적합도 의미 비교 방식

상태: 제안 — 합성 fixture 평가 후 LLM 판정 계약을 확정한다.

## 결정된 방향

- Java는 필수·우대 기술 등 명확한 조건을 규칙으로 판정한다.
- Python은 공고 담당 업무와 사용자 프로젝트 업무의 의미 관계만 비교한다.
- MVP 분석 경로에서는 임베딩 생성과 코사인 점수 계산을 사용하지 않는다.
- Ollama와 Gemini는 채용공고 추출과 근거 기반 LLM 분석에 집중한다.
- Ollama를 기본 Provider로 사용하고 Gemini는 실패 시 폴백과 제한적인 교차검증에 사용한다.
- Gemini API를 프로젝트에서 제거하지 않는다.
- 조건 판정과 의미 분석을 `overallSimilarity` 같은 불투명한 총점으로 합치지 않는다.

상세 경계는 [내부 계약](../../contracts/job-evidence-similarity.md)을 따른다.

## 임베딩을 MVP에서 제외한 이유

`nomic-embed-text`는 같은 백엔드 업무 `0.9948`보다 관련 없는 프론트엔드 업무를
`0.9980`으로 더 높게 반환했고 서로 다른 기술명도 `1.0000`으로 뭉쳤다. Gemini 임베딩은
과거 작은 표본에서 구분력이 더 좋았지만, MVP에서는 임베딩 호출에 별도 토큰·실행 자원을
배분하지 않기로 했다.

이는 Gemini 품질 실패나 Gemini API 제거를 뜻하지 않는다. 생성 모델 호출은 공고 추출과
근거 기반 분석에 사용하고 상시 병행 대신 Ollama 기본·Gemini 폴백 정책을 적용한다.

## LLM-as-judge 평가

LLM은 자유 문장이 아니라 `RELATED` 또는 `NOT_RELATED`와 입력에 존재하는 근거 식별자만
반환한다. 숫자 점수, confidence, 기술 보유 판정과 합격 가능성은 만들지 않는다.

1. 병합된 합성공고 fixture로 직군별 업무 근거를 구성한다.
2. Ollama 기본 경로의 구분력, 반복 안정성, 근거 타당성과 지연시간을 측정한다.
3. Ollama 실패 시 Gemini 폴백이 같은 계약을 지키는지 제한적으로 확인한다.
4. 품질 게이트 통과 후 provider·model·판정 enum을 확정한다.
5. Python endpoint와 Java client를 구현하고 연결·브라우저 테스트를 수행한다.

### 2026-08-12 평가 결과 (2단계 — Ollama 기본 경로)

`ai-python/evaluation/job_evidence_judge_spike.py`로 후보 3개 모델을 판정 과제로 비교했다.
`tests/fixtures/job_postings/`의 17개 공고 담당 업무를 그대로 옮겨 job 근거로 쓰고,
도메인별 사용자 프로젝트 근거 7개(합성) 전체와 비교해 best-match와 `RELATED`/`NOT_RELATED`를
받았다. 각 job × 3회 반복(temperature 0).

| 모델 | best-match | RELATED 분류 | 근거 유효 | 비결정성 | 평균 |
|---|---:|---:|---:|---:|---:|
| **qwen2.5:latest** | **36/36** | **42/42** | **51/51** | 0 | 1.2s |
| exaone3.5:latest | 30/36 | 36/42 | 51/51 | 0 | 1.2s |
| llama3.2:latest | 18/36 | 36/42 | 51/51 | 0 | 0.9s |

`qwen2.5:latest`는 채점 대상 14개 job에서 6개 도메인을 정확히 구분하고, 대응 프로젝트가 없는
게임 서버·QA를 `NOT_RELATED`로 걸러 품질 게이트 전 항목을 통과했다. 임베딩(nomic)이 게이트를
실패한 것과 정반대다. exaone3.5는 결제·정산 백엔드를 infra로, 게임 서버·QA를 `RELATED`로
오판했고, llama3.2는 대부분을 backend·data-science로 붕괴시켜 둘 다 부적합이다.

한계: 사용자 프로젝트 근거는 도메인별 1개 합성 표본이라 도메인 구분력만 증명하며, 실제 시장
정확도나 유사 프로젝트가 경합하는 난이도는 검증하지 않았다(공고 fixture와 같은 단서).
정답이 모호한 job(ai_ml↔data-science, fullstack↔backend/frontend, llm_rag↔backend)은 집계에서
제외했다. 사용자 근거 다수 경합 난이도는 아직 검증 전이다.

### 2026-08-12 Gemini 폴백 부분 평가 (3단계)

같은 데이터셋·프롬프트·채점(`ai-python/evaluation/job_evidence_judge_gemini_check.py`,
스파이크에서 import)으로 `gemini-flash-latest`를 교차 검증했다. 성공한 21회는 전부 정답이었으나
(best-match 20/20, 분류 21/21, 근거유효 21/21, 평균 3.6s), **무료 등급 "하루 20회" 한도**
(`GenerateRequestsPerDayPerProjectPerModel-FreeTier`, gemini-3.6-flash)에 막혀 나머지 30회가
429였다. 커버된 도메인이 backend·frontend에 편중되고 security·mobile·infra·game-server(핵심
NOT_RELATED) 등은 한 번도 평가되지 못해 **판정 능력을 "통과"로 결론낼 수 없다**(비결론).

이 하루 20회 한도 자체가 반복 가능한 전체 평가(51회 = 3일치)를 무료 등급에서 불가능하게 만들며,
Gemini를 폴백·제한적 교차검증으로만 쓰고 실시간 주경로에서 배제하는 결정을 뒷받침한다.

**2026-08-13 재평가(REPEATS=1, 17회)**: 쿼터 리셋 후 재시도했으나 오늘 여유분이 ~10회뿐이라
10회 성공 뒤 다시 429였다(일일 한도가 0으로 완전히 리셋되지 않음). 성공한 10회는 전부 정답
(best-match 8/8, 분류 10/10)이며 어제 못 본 devops(infra)·game-server·qa(핵심 NOT_RELATED 둘)를
포함했다. **어제+오늘 누적 31/31 전부 정답**, 커버 도메인 7개(backend·frontend·data-eng·
data-science·devops·game-server·qa) — Gemini 폴백 판정은 테스트된 범위에서 신뢰할 만하다.
다만 mobile·security·cloud는 여전히 미평가이고, 무료 등급으로는 `REPEATS=1`(17회)조차 못 채워
깨끗한 단일 전체 평가가 불가함이 재확인됐다(완전 결론은 유료 등급 필요).

## 관련 문서

- [채용공고 근거 의미 비교 내부 계약](../../contracts/job-evidence-similarity.md)
- [임베딩 실험 기록](embedding-similarity.md)
- [합성 채용공고 fixture](../testing/synthetic-job-posting-fixtures.md)
- [공공기관 채용공고 분석 책임 경계](public-institution-job-analysis.md)
