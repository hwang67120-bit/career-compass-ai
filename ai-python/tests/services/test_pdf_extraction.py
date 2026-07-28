import pytest
from fpdf import FPDF

from app.services.pdf_extraction import (
    PdfNoExtractableTextError,
    PdfUnreadableError,
    extract_pdf_text,
)

KOREAN_FONT_PATH = "C:/Windows/Fonts/malgun.ttf"


def make_pdf_bytes(page_texts: list[str]) -> bytes:
    pdf = FPDF()
    pdf.add_font("Malgun", fname=KOREAN_FONT_PATH)
    for text in page_texts:
        pdf.add_page()
        if text:
            pdf.set_font("Malgun", size=12)
            pdf.cell(text=text)
    return bytes(pdf.output())


def test_extract_pdf_text_returns_text_per_page() -> None:
    pdf_bytes = make_pdf_bytes(["첫 번째 페이지 이력서 내용", "두 번째 페이지 프로젝트 경험"])

    result = extract_pdf_text(pdf_bytes)

    assert len(result) == 2
    assert result[0].page_number == 1
    assert "이력서" in result[0].text
    assert result[1].page_number == 2
    assert "프로젝트" in result[1].text


def test_extract_pdf_text_skips_pages_without_text() -> None:
    pdf_bytes = make_pdf_bytes(["텍스트가 있는 페이지", ""])

    result = extract_pdf_text(pdf_bytes)

    assert len(result) == 1
    assert result[0].page_number == 1


def test_extract_pdf_text_raises_when_no_page_has_text() -> None:
    pdf_bytes = make_pdf_bytes(["", ""])

    with pytest.raises(PdfNoExtractableTextError):
        extract_pdf_text(pdf_bytes)


def test_extract_pdf_text_raises_when_file_is_not_a_pdf() -> None:
    with pytest.raises(PdfUnreadableError):
        extract_pdf_text(b"this is not a pdf file")
