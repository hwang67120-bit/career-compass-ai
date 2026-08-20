from app.guardrails.contact_info_redaction import redact_contact_info


def test_redact_contact_info_removes_email() -> None:
    text = "문의: hr.team@example.co.kr 로 연락 바랍니다."

    redacted = redact_contact_info(text)

    assert "hr.team@example.co.kr" not in redacted
    assert "[REDACTED]" in redacted


def test_redact_contact_info_removes_korean_mobile_number() -> None:
    text = "담당자 연락처: 010-1234-5678"

    redacted = redact_contact_info(text)

    assert "010-1234-5678" not in redacted
    assert "[REDACTED]" in redacted


def test_redact_contact_info_removes_landline_without_hyphens() -> None:
    text = "대표번호 0212345678로 문의하세요."

    redacted = redact_contact_info(text)

    assert "0212345678" not in redacted


def test_redact_contact_info_leaves_unrelated_text_unchanged() -> None:
    text = "백엔드 개발자를 채용합니다. 필수 조건: Python 3년 이상."

    redacted = redact_contact_info(text)

    assert redacted == text
