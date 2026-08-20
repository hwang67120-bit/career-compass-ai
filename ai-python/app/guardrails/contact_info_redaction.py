"""채용공고 원문을 외부 LLM(Gemini)으로 보내기 전 연락처 정보를 지운다.

`AGENTS.md` "개인정보와 보관" — "Python과 외부 AI에는 개인정보가 제거된
최소 필드와 최소 근거만 전달한다"를 채용공고 Gemini 폴백 경로에 적용한다.
Ollama는 로컬 실행이라 외부 전송이 아니므로 이 처리를 거치지 않는다
(`app/services/job_posting_extraction.py`의 Gemini 폴백 호출 직전에만 적용).

이메일·전화번호 정규식은 흔히 쓰는 표기만 잡는다 — 문자로 풀어 쓴 숫자,
비표준 구분자 같은 변형 표기까지는 못 잡는 알려진 한계다. 실제 채용공고
표본으로 검증되지 않았으므로 패턴 커버리지 확정은 확인 필요로 남긴다.
"""

import re

_EMAIL_PATTERN = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_PHONE_PATTERN = re.compile(r"0\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}")

_REDACTED = "[REDACTED]"


def redact_contact_info(text: str) -> str:
    """이메일·전화번호로 보이는 부분을 `[REDACTED]`로 바꾼 새 문자열을 반환한다."""
    text = _EMAIL_PATTERN.sub(_REDACTED, text)
    text = _PHONE_PATTERN.sub(_REDACTED, text)
    return text
