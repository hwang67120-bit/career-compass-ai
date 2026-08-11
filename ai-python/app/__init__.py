"""`app` 패키지가 처음 import될 때 SSL 인증서 검증을 OS 신뢰 저장소로 전환한다.

로컬 개발 환경에서 백신(Avast로 추정)의 HTTPS 검사가 TLS 연결을 자체
인증서로 가로채는데, Python 기본 인증서 묶음(certifi)에는 그 인증서가
없어서 Gemini 같은 외부 HTTPS 호출이 전부 `CERTIFICATE_VERIFY_FAILED`로
막혔다(실제 확인, 2026-08-11). Windows 자체는 그 인증서를 신뢰하므로,
`truststore`로 Python이 OS 인증서 저장소를 쓰게 만들면 백신 설정을
건드리지 않고도 해결된다.

가장 먼저 실행돼야 한다 — `httpx`·`google-genai` 등이 SSL 컨텍스트를
만들기 전에 패치해야 하므로 `app` 패키지의 다른 모든 하위 모듈 import보다
앞서는 이 파일에 둔다.
"""

import truststore

truststore.inject_into_ssl()
