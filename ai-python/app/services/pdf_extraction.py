"""PDF 이력서·포트폴리오에서 텍스트를 추출한다."""

from io import BytesIO

from pypdf import PdfReader
from pypdf.errors import PyPdfError

from app.schemas.document import PageText


class PdfUnreadableError(RuntimeError):
    """PDF 파일 자체를 열 수 없는 경우다 (손상, 암호화, 빈 파일 등)."""


class PdfNoExtractableTextError(RuntimeError):
    """어떤 페이지에서도 텍스트를 추출하지 못한 경우다 (스캔 PDF로 추정)."""


def extract_pdf_text(pdf_bytes: bytes) -> list[PageText]:
    """PDF 바이트에서 페이지별 텍스트를 추출한다.

    입력:
        pdf_bytes: PDF 파일의 원본 바이트.

    반환:
        텍스트가 있는 페이지만 담은 목록(페이지 번호는 1부터 시작). 텍스트가
        없는 페이지는 빈 문자열로 채우지 않고 목록에서 제외한다.

    예외:
        PdfUnreadableError: PDF 형식이 손상됐거나 열 수 없는 경우.
        PdfNoExtractableTextError: 어떤 페이지에서도 텍스트를 추출하지
            못한 경우. 스캔 PDF는 OCR로 보완하지 않고 이 오류로 처리한다.
    """
    try:
        reader = PdfReader(BytesIO(pdf_bytes))
        pages = reader.pages
    except PyPdfError as error:
        raise PdfUnreadableError("PDF 파일을 열 수 없습니다.") from error

    extracted: list[PageText] = []
    for index, page in enumerate(pages, start=1):
        text = page.extract_text().strip()
        if text:
            extracted.append(PageText(page_number=index, text=text))

    if not extracted:
        raise PdfNoExtractableTextError(
            "PDF에서 텍스트를 추출하지 못했습니다. 스캔 PDF는 지원하지 않습니다."
        )

    return extracted
