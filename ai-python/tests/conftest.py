"""회사망 TLS 검사(프록시) 환경에서 로컬 테스트 실행 시에만 시스템 인증서 저장소를 사용하도록 한다.

운영 코드(app/)에는 적용하지 않는다. 배포 환경의 인증서 문제는 여기서 다루지 않는다.
"""

import truststore

truststore.inject_into_ssl()
