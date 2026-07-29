"""PDF에서 추출한 원문에서 개인정보를 제거한다.

계약: contracts/document-extraction.md 7절(개인정보 가드레일) — 개인정보
제거 전에 Ollama·Gemini를 호출하지 않는다.

이메일·전화번호·주민등록번호는 backend-java의
`BasicPersonalInformationSanitizer`와 동일한 정규식 치환 패턴을 그대로
재사용한다. 이름은 Java 쪽에도 없는 정규식 기반 휴리스틱을 추가로 둔다 —
Java가 Python에 실명을 아예 전달하지 않으므로(계약 3절) Python은 정답
이름을 알 수 없고, "이름처럼 보이는 패턴"만 마스킹할 수 있다.

문서 전체에서 "흔한 성씨로 시작하는 한글 단어"를 찾으면 "백엔드", "기반",
"문제", "고객"처럼 성씨 글자로 시작하는 일반 단어까지 대량으로 오탐한다
(실제로 확인됨). 그래서 각 텍스트 블록(페이지)의 **맨 앞 토큰**만 검사한다
— 이력서는 관례상 맨 앞에 이름이 오는 경우가 많고("김도현 - 백엔드
개발자"), 본문 중간 단어는 건드리지 않아서 오탐 범위가 훨씬 좁다. 다만
이것도 완전하지 않다 — 페이지가 우연히 성씨로 시작하는 일반 단어로
시작하면 그 단어만 오탐할 수 있고, "이름: 김도현"처럼 이름이 맨 앞이
아니면 놓친다. 완전한 이름 검출이 아니라 프롬프트 지시만으로는 안 되던
부분을 보강하는 2차 방어선이다.
"""

import re

_EMAIL_PATTERN = re.compile(
    r"(?<![\w.!#$%&'*+/=?^`{|}~-])[\w.!#$%&'*+/=?^`{|}~-]+@[\w-]+(?:\.[\w-]+)+(?![\w-])",
    re.IGNORECASE,
)
_PHONE_PATTERN = re.compile(r"(?<!\d)(?:01[016789]|0\d{1,2})[- ]?\d{3,4}[- ]?\d{4}(?!\d)")
_RESIDENT_NUMBER_PATTERN = re.compile(r"(?<!\d)\d{6}[- ]?[1-4]\d{6}(?!\d)")

_COMMON_KOREAN_SURNAMES = (
    "남궁|황보|선우|독고|제갈|"
    "김|이|박|최|정|강|조|윤|장|임|한|오|서|신|권|황|안|송|유|홍|전|고|문|양|손|배|백|허|"
    "남|심|노|하|곽|성|차|주|우|구|나|민|진|지|엄|채|원|천|방|공|현|함|변|염|여|추|도|소|"
    "석|선|설|마|길|연|위|표|명|기|반|왕|금|옥|육|인|맹|제|모"
)
_NAME_AT_START_PATTERN = re.compile(
    rf"^(?:{_COMMON_KOREAN_SURNAMES})[가-힣]{{1,2}}(?![가-힣])"
)


def sanitize_personal_information(text: str) -> str:
    """이메일·전화번호·주민등록번호·(맨 앞) 이름을 마스킹 태그로 치환한다."""
    sanitized = _EMAIL_PATTERN.sub("[EMAIL]", text)
    sanitized = _PHONE_PATTERN.sub("[PHONE]", sanitized)
    sanitized = _RESIDENT_NUMBER_PATTERN.sub("[RESIDENT_REGISTRATION_NUMBER]", sanitized)
    return _NAME_AT_START_PATTERN.sub("[NAME]", sanitized, count=1)
