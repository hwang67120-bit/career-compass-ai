"""PDF에서 추출한 원문에서 개인정보를 제거한다.

계약: contracts/document-extraction.md 7절(개인정보 가드레일) — 개인정보
제거 전에 Ollama·Gemini를 호출하지 않는다.

방식은 backend-java의 `BasicPersonalInformationSanitizer`와 동일한
정규식 치환 패턴(이메일·전화번호·주민등록번호)을 그대로 재사용한다.
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
