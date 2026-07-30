"""PDF에서 추출한 원문에서 개인정보를 제거한다.

계약: contracts/document-extraction.md 7절(개인정보 가드레일) — 개인정보
제거 전에 Ollama·Gemini를 호출하지 않는다.

이메일·전화번호·주민등록번호는 backend-java의
`BasicPersonalInformationSanitizer`와 동일한 정규식 치환 패턴을 그대로
재사용한다.

이름은 여기서 정규식으로 제거하지 않는다. 성씨 목록 기반 휴리스틱을
시도했다가 되돌렸다 — 문서 전체에 적용하면 "백엔드", "이력서", "전공"
같은 일반 단어를 대량 오탐했고(실제로 확인됨), 탐지 범위를 좁히면 문장
중간 이름·영문 이름을 놓쳐서 정규식만으로는 정확도와 범위를 동시에
만족시키지 못했다. 대신 `app/providers/ollama.py`,
`app/providers/gemini.py`의 프롬프트 지시("이름 ... 어떤 필드나 근거에도
포함하지 않는다")로 LLM이 이름을 결과에 넣지 않게 한다 — 완벽한 보장은
아니며, 정확한 이름 제거가 필요해지면 NER 같은 별도 방식을 검토한다.
"""

import re

_EMAIL_PATTERN = re.compile(
    r"(?<![\w.!#$%&'*+/=?^`{|}~-])[\w.!#$%&'*+/=?^`{|}~-]+@[\w-]+(?:\.[\w-]+)+(?![\w-])",
    re.IGNORECASE,
)
_PHONE_PATTERN = re.compile(r"(?<!\d)(?:01[016789]|0\d{1,2})[- ]?\d{3,4}[- ]?\d{4}(?!\d)")
_RESIDENT_NUMBER_PATTERN = re.compile(r"(?<!\d)\d{6}[- ]?[1-4]\d{6}(?!\d)")


def sanitize_personal_information(text: str) -> str:
    """이메일·전화번호·주민등록번호를 마스킹 태그로 치환한다."""
    sanitized = _EMAIL_PATTERN.sub("[EMAIL]", text)
    sanitized = _PHONE_PATTERN.sub("[PHONE]", sanitized)
    return _RESIDENT_NUMBER_PATTERN.sub("[RESIDENT_REGISTRATION_NUMBER]", sanitized)
