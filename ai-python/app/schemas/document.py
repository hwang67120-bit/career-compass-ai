"""문서(PDF 등)에서 추출한 페이지 텍스트의 스키마를 정의한다."""

from pydantic import BaseModel, ConfigDict


class PageText(BaseModel):
    """문서 한 페이지에서 추출한 텍스트다."""

    model_config = ConfigDict(extra="forbid")

    page_number: int
    text: str
