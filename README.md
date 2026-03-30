# Coupon Backend System

수만 명이 동시에 몰리는 선착순 쿠폰 이벤트에서 **초과 발급 없이 정확한 수량을 발급하고, 서버가 다운되지 않는 것**을 목표로 합니다.

동일한 도메인 요구사항을 세 가지 기술 설계로 구현하며, 각 단계에서 발생하는 병목을 실측하고 다음 단계에서 해소하는 **점진적 고도화** 방식으로 진행했습니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language / Framework | Java 17, Spring Boot 3.4.4 |
| Database | MySQL 8.0, Redis 7.x |
| Message Queue | Apache Kafka |
| Build / Deploy | Gradle, Docker |
| Load Testing | K6 |

---

## 모듈 구성

```
coupon-backend/
├── user-module          # 공유 도메인 라이브러리 (User 엔티티, 공통 예외, 응답 포맷)
├── issuer-app           # Task 1: DB 비관적 락 적용 발급 서버
├── issuer-redis-app     # Task 2: Redis Lua Script 적용 발급 서버
├── gate-app             # Task 3: 대기열 서버 — 트래픽 셰이핑
├── issuer-api-app       # Task 3: API 요청 기반 발급 서버 (default)
├── issuer-worker-app    # Task 3: 비동기 메시지 처리 기반 발급 서버
└── scripts/             # K6 부하 테스트 스크립트, DB 초기화 SQL
```

---

## 아키텍처 진화

| Task | 모듈 | 핵심 설계 | 한계                                    |
|------|------|-----------|---------------------------------------|
| Task 1 | `issuer-app` | DB 비관적 락으로 동시성 제어 | 서버 확장 시 lock contention 악화 → TPS 개선 없음 |
| Task 2 | `issuer-redis-app` | Redis Lua Script으로 동시성 제어 주체를 DB → Redis로 이전 | Redis 장애 시 재고 상태 소실 → 데이터 정합성 위험      |
| Task 3 | `gate-app` + `issuer-api-app` | 대기열로 발급 서버에 도달하는 트래픽 자체를 제어 | —                                     |

### Redis HA — Task 2

Task 2에서 Redis는 쿠폰 재고, 쿠폰 발급 이력 등 비즈니스 데이터의 **SSOT**입니다. Redis 장애 시 재고 상태가 소실되면 초과 발급으로 직결되므로 HA 확보가 필수였습니다.

**Redis Sentinel + AOF(everysec)** 를 적용했습니다. Master 장애 시 Sentinel 과반수 합의로 Replica를 자동 승격하고, AOF 설정으로 데이터 유실을 최대 1초치 이내로 최소화합니다.

---

## 최종 아키텍처 (Task 3)

### 전체 흐름

```
[클라이언트]
    │
    ├─ POST /gate/events/{eventId}/enqueue  ──→  [gate-app]  →  Redis 대기열(ZSet) 적재
    │
    └─ GET  /gate/events/{eventId}/rank     ──→  [gate-app]  →  순번 조회 (폴링)
                                                      │
                                              [DispatchScheduler]
                                               매 3초 주기 실행
                                                      │
                                         ┌────────────┴────────────┐
                                   active-queue 모드           kafka 모드
                                      (기본 경로)               (선택 경로)
                                         │                         │
                               Active Queue 키 생성            Kafka Topic 발행
                               (TTL 60초, Redis)                   │
                                         │                  [issuer-worker-app]
                                         │                   Kafka Consumer
                                         │                   → DB 발급 처리
                               클라이언트가 ACTIVE 확인 후
                               issuer-api-app 직접 호출
                                         │
                                [issuer-api-app]
                                 Active Queue 검증
                                 → 재고 차감 → INSERT
```

### dispatch-mode 선택

`gate.dispatch-mode` 설정값으로 발급 경로를 결정합니다.

| 설정값                 | 구현체 | 발급 경로 |
|---------------------|--------|-----------|
| `active-queue` (기본) | `ActiveQueueDispatchStrategy` | gate-app → Active Queue(TTL) → 클라이언트가 issuer-api-app 직접 호출 |
| `kafka` (옵션)        | `KafkaDispatchStrategy` | gate-app → Kafka → issuer-worker-app |

### Redis 데이터 구조 (gate-app)

| 키 | 타입 | 역할 |
|----|------|------|
| `gate:queue:{eventId}` | ZSet | 대기열 (score = 티켓 번호) |
| `gate:queue:rank:{eventId}` | String | 티켓 번호 카운터 (INCR) |
| `gate:processing:{eventId}` | ZSet | 처리 중 유저 (score = 진입 timestamp) |
| `gate:processing:rank:{eventId}` | Hash | 처리 중 유저의 원래 티켓 번호 (재큐 복원용) |
| `gate:active:{eventId}:{userId}` | String | 발급 권한 키 (TTL = 60초) |

### 전체 데이터 흐름

```
① 유저  →  POST /gate/events/{id}/enqueue
           enqueueLua(): ZSet 등록 + 티켓 번호 발급
           응답: { status: "ENQUEUED", rank: 1234 }

② 유저  →  GET /gate/events/{id}/rank  (폴링)
           ZSet ZRANK 조회
           응답: { status: "WAITING", rank: 850 }

③ DispatchScheduler (3초 주기)
           popToActiveLua(): ZSet 상위 N명 제거 + TTL 키 생성

④ 유저  →  GET /gate/events/{id}/rank
           ZSet ZRANK == null, isActive() == true
           응답: { status: "ACTIVE" }

⑤ 유저  →  POST /issue/events/{id}?userId={userId}
           issuer-api-app: Active 검증 → 재고 원자적 차감 → INSERT
           응답: { result: "SUCCESS" }
```
