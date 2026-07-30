"""PDF에서 추출한 원문에서 개인정보를 제거한다.

계약: contracts/document-extraction.md 7절(개인정보 가드레일) — 개인정보
제거 전에 Ollama·Gemini를 호출하지 않는다.

이메일·전화번호·주민등록번호는 backend-java의
`BasicPersonalInformationSanitizer`와 동일한 정규식 치환 패턴을 그대로
재사용한다. 이름은 Java 쪽에도 없는 정규식 기반 휴리스틱을 추가로 둔다. Java는
원본 PDF를 그대로 Python에 전달하므로 이름은 원문 텍스트 안에 그대로
들어있다 — Java가 안 보내는 건 "이게 진짜 이름이다"라고 확인해 주는
별도 필드다. 그래서 Python은 정답 이름과 대조할 방법이 없고,
"이름처럼 보이는 패턴"만 마스킹할 수 있다.

문서 전체에서 "흔한 성씨로 시작하는 한글 단어"를 찾으면 "백엔드", "기반",
"문제", "고객"처럼 성씨 글자로 시작하는 일반 단어까지 대량으로 오탐한다
(실제로 확인됨). 그래서 두 가지 좁은 범위만 검사한다.

1. `이름`/`성명` 같은 명시적 라벨 바로 뒤에 오는 한글 토큰("이름: 김철수").
   라벨 자체가 일반 단어일 위험이 낮아서 문서 전체에 적용해도 안전하다.
2. 각 텍스트 블록(페이지)의 **맨 앞 토큰**("김도현 - 백엔드 개발자"처럼
   이력서 관례상 이름이 맨 앞에 오는 경우).

2번은 "이력서", "전공"처럼 흔한 성씨 글자로 시작하는 이 도메인의 일반
단어도 맨 앞에 오면 오탐한다(실제로 확인됨) — `_NOT_A_NAME_WORDS`에
확인된 단어만 예외 처리했고, 이 목록은 완전하지 않다. 다른 도메인 단어가
우연히 이 위치에서 오탐할 수 있다.

**알려진 한계(확인 필요)**: 문장 중간에 라벨 없이 나오는 이름
("...김철수는 3년간...")과 영문 이름은 이 두 패턴 다 못 잡는다. 탐지
범위를 문서 전체로 넓히면 일반 단어 오탐이 그대로 재발하는 근본적인
트레이드오프가 있어서(1번 문단 참고), 정규식만으로는 더 넓히지 않았다.
완전히 막으려면 NER 등 별도 방식이 필요하며, 이건 프롬프트 지시만으로는
안 되던 부분을 보강하는 2차 방어선일 뿐 최종 차단선이 아니다.
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
_NOT_A_NAME_WORDS = {"이름", "성명", "성함", "이력서", "전공"}
_NAME_AT_START_PATTERN = re.compile(
    rf"^(?:{_COMMON_KOREAN_SURNAMES})[가-힣]{{1,2}}(?![가-힣])"
)
_LABELED_NAME_PATTERN = re.compile(
    r"(?P<label>이름|성명)(?P<sep>\s*[:：]\s*|\s+)(?P<name>[가-힣]{2,4})(?![가-힣])"
)


def _mask_labeled_name(match: re.Match[str]) -> str:
    return f"{match.group('label')}{match.group('sep')}[NAME]"


def _mask_name_at_start(match: re.Match[str]) -> str:
    # "이름"·"이력서"·"전공"처럼 흔한 성씨 글자로 시작하는 이 도메인의
    # 일반 단어를 이름으로 오인하지 않도록 예외 처리한다. 이 목록은
    # 확인된 사례를 모은 것일 뿐 전부를 막지는 못한다(위 모듈 설명 참고).
    if match.group(0) in _NOT_A_NAME_WORDS:
        return match.group(0)
    return "[NAME]"


def sanitize_personal_information(text: str) -> str:
    """이메일·전화번호·주민등록번호·이름(라벨 뒤 또는 맨 앞)을 마스킹 태그로 치환한다."""
    sanitized = _EMAIL_PATTERN.sub("[EMAIL]", text)
    sanitized = _PHONE_PATTERN.sub("[PHONE]", sanitized)
    sanitized = _RESIDENT_NUMBER_PATTERN.sub("[RESIDENT_REGISTRATION_NUMBER]", sanitized)
    sanitized = _LABELED_NAME_PATTERN.sub(_mask_labeled_name, sanitized)
    return _NAME_AT_START_PATTERN.sub(_mask_name_at_start, sanitized, count=1)
