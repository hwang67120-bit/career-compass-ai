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


def test_sanitize_does_not_remove_names_by_regex() -> None:
    """이름은 여기서 정규식으로 제거하지 않는다 — 프롬프트 지시에 맡긴다
    (이유: 문서 docstring 참고, 성씨 목록 기반 탐지는 오탐이 심해서 되돌림)."""
    text = "김철수 - 백엔드 개발자"

    assert sanitize_personal_information(text) == text
