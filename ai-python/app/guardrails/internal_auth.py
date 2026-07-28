"""Java에서 온 요청인지 확인하는 내부 인증 가드레일이다."""

import hmac

from fastapi import Depends, Header, HTTPException, status

from app.guardrails.settings import InternalAuthSettings, get_internal_auth_settings


def verify_internal_token(
    x_internal_token: str = Header(...),
    settings: InternalAuthSettings = Depends(get_internal_auth_settings),  # noqa: B008
) -> None:
    """요청 헤더의 내부 서비스 토큰이 설정된 값과 일치하는지 확인한다.

    입력:
        x_internal_token: 요청 헤더 `X-Internal-Token` 값.
        settings: 환경변수에서 읽은 내부 인증 설정.

    반환:
        반환값이 없다. 일치하지 않으면 예외를 발생시킨다.

    예외:
        HTTPException: 토큰이 일치하지 않으면 401을 반환한다.
    """
    if not hmac.compare_digest(x_internal_token, settings.internal_service_token):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service token.",
        )
