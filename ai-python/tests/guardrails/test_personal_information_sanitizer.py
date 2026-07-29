from app.guardrails.personal_information_sanitizer import sanitize_personal_information


def test_sanitize_masks_email() -> None:
    result = sanitize_personal_information("연락처: hong.gildong@example.co.kr 입니다.")

    assert "hong.gildong@example.co.kr" not in result
    assert "[EMAIL]" in result


def test_sanitize_masks_phone_number() -> None:
    result = sanitize_personal_information("전화번호는 010-1234-5678 입니다.")

    assert "010-1234-5678" not in result
    assert "[PHONE]" in result


def test_sanitize_masks_resident_registration_number() -> None:
    result = sanitize_personal_information("주민등록번호 901231-1234567")

    assert "901231-1234567" not in result
    assert "[RESIDENT_REGISTRATION_NUMBER]" in result


def test_sanitize_keeps_unrelated_text_unchanged() -> None:
    text = "Java, Spring Boot 3년 경력. ABC회사에서 결제 시스템을 개발했다."

    assert sanitize_personal_information(text) == text


def test_sanitize_masks_name_at_start_of_text() -> None:
    """이력서 관례상 이름이 맨 앞에 오는 경우("이름 - 직무")를 잡는다."""
    result = sanitize_personal_information("김철수 - 백엔드 개발자")

    assert "김철수" not in result
    assert result == "[NAME] - 백엔드 개발자"


def test_sanitize_does_not_mask_common_words_mid_text() -> None:
    """성씨 글자로 시작하는 흔한 단어를 본문 중간에서 오탐하지 않는다.

    맨 앞이 아닌 위치의 '백엔드'는 성씨 패턴에 맞아도 건드리지 않는다.
    """
    text = "3년차 백엔드 개발자. Spring Boot 기반 REST API 설계·구현"

    assert sanitize_personal_information(text) == text


def test_sanitize_does_not_mask_name_pattern_when_not_at_start() -> None:
    """이름이 문장 중간에 있으면 지금 휴리스틱으로는 못 잡는다(알려진 한계)."""
    text = "백엔드 개발자 김철수는 3년 경력이다."

    result = sanitize_personal_information(text)

    assert "김철수" in result  # 알려진 한계: 맨 앞이 아니면 검출 못 함
