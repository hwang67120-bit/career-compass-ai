# 백엔드 분석 작업과 SSE 처리 결정

상태: 아키텍처 방향 확정, 제한값과 세부 API는 확인 필요

## 목적

Java 서버가 GitHub·Python 같은 외부 서비스의 지연 때문에 DB 트랜잭션을 오래 유지하지 않고,
분석 상태와 사용자 진행 이벤트를 일관되게 저장·전달하기 위한 기준을 정의한다.

## 현재 확정 사항

### PostgreSQL 작업 큐

- 분석 요청은 실행 전에 PostgreSQL에 `QUEUED` 상태로 저장한다.
- Worker는 저장된 작업을 하나만 선점한 뒤 `RUNNING`으로 변경한다.
- 서버가 재시작되어도 아직 실행하지 않은 작업은 DB에 남아 있어야 한다.
- 단순 `@Async` 호출만으로 분석 작업의 생명주기를 관리하지 않는다.
- 첫 배포에서는 PostgreSQL을 작업 상태의 단일 기준으로 사용한다.

### 외부 API와 트랜잭션 분리

- GitHub API와 Python 호출은 DB 트랜잭션 밖에서 실행한다.
- 트랜잭션은 작업 생성, 작업 선점, 상태 변경, 결과 저장처럼 DB 변경이 필요한 짧은 구간에만 사용한다.
- 외부 호출에는 설정으로 관리하는 연결·응답 제한시간을 적용한다.

### 상태와 이벤트 원자 저장

- 분석 상태 변경과 그 상태를 알리는 이벤트는 같은 DB 트랜잭션에서 저장한다.
- 완료·실패·취소가 경합하면 현재 상태와 버전을 확인하는 조건부 갱신 또는 낙관적 잠금을 사용한다.
- 조건에 맞는 행이 변경되지 않으면 다른 요청이 먼저 상태를 바꾼 것으로 처리하며 결과를 덮어쓰지 않는다.

### DB 커밋 후 SSE 전송

- SSE는 DB 커밋이 성공한 뒤에만 전송한다.
- 트랜잭션 안에서 `SseEmitter.send`를 호출하지 않는다.
- 이벤트는 메모리에만 보관하지 않고 DB에 저장한다.
- 재연결 시 `Last-Event-ID` 이후의 사용자 소유 이벤트를 DB에서 순서대로 재전송한다.
- SSE 연결이 끊겨도 분석 작업은 계속하며 상태 조회 API로 복구할 수 있어야 한다.

## Redis 도입 기준

첫 배포에서는 Redis를 필수 구성으로 사용하지 않는다. 다음 조건이 실제로 확인되면 역할별로 도입을 검토한다.

| 확인된 상황 | 검토할 Redis 역할 |
| --- | --- |
| Java 서버를 두 대 이상 실행 | 인스턴스 사이 SSE 전달 신호용 Pub/Sub |
| 여러 Java 서버가 사용자별 요청 제한을 공유 | 원자 카운터와 만료 기반 제한 상태 |
| PostgreSQL 작업 선점이 측정된 병목 | Redis Streams 작업 큐와 소비자 그룹 |
| 반복 조회가 측정된 병목 | 변경이 적은 데이터의 제한적 캐시 |
| DB 제약과 조건부 갱신으로 해결할 수 없는 공유 자원 경합 | 분산 잠금 검토 |

Redis Pub/Sub는 연결이 끊긴 구독자에게 메시지를 다시 전달하지 않으므로 이벤트의 영구 기준으로 사용하지 않는다.
Redis를 추가한 뒤에도 분석 상태와 재연결용 이벤트의 기준은 PostgreSQL로 유지한다.
Redis Streams로 작업 큐를 변경할 때는 PostgreSQL 큐와 동시에 두 개의 기준을 만들지 않고 전환 범위를 별도로 확정한다.

## 보안과 사용자 격리

- SSE 사용자 식별자는 요청 파라미터가 아니라 인증이 끝난 Security Context에서 가져온다.
- 분석과 이벤트는 `jobAnalysisId`와 현재 사용자 식별자를 함께 사용해 조회한다.
- 다른 사용자의 분석은 존재 여부를 노출하지 않도록 사용자 API 계약의 `404` 규칙을 따른다.
- 이벤트 payload에 원문, 저장소 파일 내용, 개인정보, 내부 토큰과 모델 원문 응답을 저장하지 않는다.
- 사용자별·전체 SSE 연결 개수와 재전송 개수는 설정으로 관리한다.

## 구현 전에 남은 결정

- 사용자별 동시 분석 최대 개수
- 서버 재시작으로 중단된 `RUNNING` 작업의 실패 처리와 수동 재시작 방법
- SSE 연결 제한시간, heartbeat 주기와 사용자별 연결 최대 개수
- 재연결 시 한 번에 재전송할 이벤트 최대 개수
- 분석 작업과 이벤트 보관 기간
- 같은 GitHub 저장소·같은 커밋의 중복 등록 처리

이 값은 실제 측정 또는 사용자 확정 없이 코드에 고정하지 않는다.

## 공식 근거

- [Spring 트랜잭션 이벤트](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
- [Spring MVC 비동기 요청과 SSE](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-async.html)
- [Spring 작업 실행과 스케줄링](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
- [PostgreSQL SELECT 잠금](https://www.postgresql.org/docs/current/sql-select.html)
- [Redis Pub/Sub 전달 특성](https://redis.io/docs/latest/develop/pubsub/)
- [Redis Streams](https://redis.io/docs/latest/develop/data-types/streams/)
- [Redis 분산 잠금](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
