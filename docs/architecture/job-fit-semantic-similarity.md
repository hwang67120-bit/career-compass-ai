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

## 관련 문서

- [채용공고 근거 의미 비교 내부 계약](../../contracts/job-evidence-similarity.md)
- [임베딩 실험 기록](embedding-similarity.md)
- [합성 채용공고 fixture](../testing/synthetic-job-posting-fixtures.md)
- [공공기관 채용공고 분석 책임 경계](public-institution-job-analysis.md)
