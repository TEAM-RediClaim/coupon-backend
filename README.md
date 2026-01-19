# 🎟️ Coupon Backend System

대규모 트래픽 상황에서도 안정적으로 선착순 쿠폰을 발급하기 위한 백엔드 시스템입니다.
**MSA(Microservices Architecture)**를 기반으로 하며, 트래픽 폭주로 인한 서버 장애를 방지하기 위해 **대기열 시스템(Traffic Shaping)**이 적용되어 있습니다.

## 🏗️ 아키텍처 및 모듈 구성 (Architecture)

이 프로젝트는 대규모 트래픽을 안정적으로 처리하는 것을 시작으로, 향후 **확장 가능한 쿠폰 플랫폼**으로 발전시키는 것을 목표로 합니다.
각 모듈은 MSA(Microservices Architecture) 원칙에 따라 철저히 역할이 분리되어 있습니다.

| 모듈명 | 역할 및 책임 (Role & Responsibility) | 기술 스택 |
| :--- | :--- | :--- |
| **gate-app** | **[Traffic Shaping & Flow Control]**<br>- 대규모 진입 트래픽의 버퍼링 및 유량 제어<br>- Redis ZSet 기반의 대기열 시스템 구현<br>- Downstream 서비스 보호를 위한 Throttling | Redis, Kafka Producer<br>Spring Boot |
| **issuer-app** | **[Coupon Issuance Processor]**<br>- 대기열을 통과한 요청에 대한 실질적 발급 트랜잭션 처리<br>- 동시성 제어 및 데이터 무결성 보장<br>- (향후) 이종 서비스 간 분산 트랜잭션 주관 | Kafka Consumer, MySQL (JPA)<br>Spring Boot |
| **core** | **[Domain Core & Shared Kernel]**<br>- 쿠폰 도메인의 핵심 비즈니스 로직 및 정책 정의<br>- 모듈 간 공유되는 DTO, Event 객체, 공통 유틸리티<br>- 플랫폼 확장을 위한 공통 규약 관리 | Java 17 (Library) |


### 🚀 향후 로드맵 (Roadmap)
현재는 **고트래픽 안정성 확보**에 집중하고 있으며, 점진적으로 아래 기술적 과제들을 수행하여 플랫폼의 완성도를 높일 계획입니다.

1. **Phase 1: 안정성 확보 (Current)**
    - 대기열 시스템(Gate)을 통한 트래픽 제어 및 서버 보호
    - Kafka를 활용한 비동기 처리 및 데이터 유실 방지

2. **Phase 2: 플랫폼 고도화**
    - 다양한 쿠폰 정책(할인율, 유효기간, 중복 발급 등)을 Core 모듈에 집약
    - 멀티 테넌트(Multi-tenant) 구조 고려

3. **Phase 3: MSA 심화 (Distributed System)**
    - **분산 트랜잭션 처리:** Saga Pattern 등을 도입하여 결제/포인트 등 타 마이크로서비스와의 정합성 보장
    - **데이터 일관성:** CDC(Change Data Capture) 및 Outbox Pattern 도입 검토

### 🔄 데이터 흐름 (Data Flow)

1. **유저 진입:** `POST /enqueue` → **Gate** (Redis 대기열 적재)
2. **대기 및 폴링:** 유저는 `GET /rank`를 통해 본인의 순번 확인 (Waiting)
3. **스케줄링:** Gate 내부 스케줄러가 설정된 속도(TPS)에 맞춰 유저를 **Processing** 상태로 변경 및 **Kafka**로 메시지 발행
4. **발급 처리:** **Issuer**가 Kafka 메시지를 소비(Consume)하여 MySQL에 쿠폰 발급 기록 저장

---

## ⚙️ 환경 설정 (Configuration)

보안을 위해 `application.yml` 파일은 Git에 포함되어 있지 않습니다.
프로젝트 실행을 위해 **각 모듈의 `src/main/resources` 경로에 `application.yml`을 직접 생성**해야 합니다.

### 1. `gate-app` 설정 예시

```yaml
spring:
  data:
    redis:
      host: localhost # K8s 배포 시 서비스명 (예: redis.rediclaim.svc.cluster.local)
      port: 6379
  kafka:
    bootstrap-servers: localhost:9092 # K8s 배포 시 서비스명
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  # Gate 전용 설정
  gate:
    event-ids: 1001, 1002
    dispatch-mode: kafka
    dispatch-per-second: 100 # 초당 처리량 제한

```

### 2. `issuer-app` 설정 예시

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rediclaim_db # K8s 배포 시 서비스명
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: coupon-group

```

---

## 🚀 개발 및 배포 워크플로우 (Workflow)

CI/CD(GitHub Actions) 없이 **로컬 환경에서 직접 빌드하여 Kubernetes(Kind)에 배포**하는 절차입니다.

### 전제 조건

* Docker Desktop 실행 중
* Docker Hub 로그인 완료 (`docker login`)
* Kubernetes 클러스터(Kind) 실행 중

### 📦 Step 1. 도커 이미지 빌드 (Build)

Gradle의 `bootBuildImage`를 사용하여 각 모듈별로 이미지를 빌드합니다. (Dockerfile 불필요)

```bash
# Gate 모듈 빌드 (태그명은 본인 환경에 맞게 수정)
./gradlew :gate-app:bootBuildImage --imageName=seongjunnoh/gate-app:latest

# Issuer 모듈 빌드
./gradlew :issuer-app:bootBuildImage --imageName=seongjunnoh/issuer-app:latest

```

### ⬆️ Step 2. 도커 허브 푸시 (Push)

Kind 클러스터가 이미지를 내려받을 수 있도록 도커 허브에 업로드합니다.

```bash
docker push seongjunnoh/gate-app:latest
docker push seongjunnoh/issuer-app:latest

```

### 🔄 Step 3. 쿠버네티스 배포 및 재시작 (Deploy)

최신 이미지를 반영하기 위해 파드를 재시작합니다. (`imagePullPolicy: Always` 설정 필수)

```bash
# YAML 설정 적용 (최초 1회 혹은 변경 시)
kubectl apply -f k8s/gate-app-deployment.yml
kubectl apply -f k8s/issuer-app-deployment.yml

# 롤아웃 재시작 (이미지 갱신)
kubectl rollout restart deployment/gate-app -n rediclaim
kubectl rollout restart deployment/issuer-app -n rediclaim

```

### ✅ Step 4. 배포 확인

```bash
# 배포 상태 모니터링
kubectl rollout status deployment/gate-app -n rediclaim

# 실시간 로그 확인
kubectl logs -n rediclaim -l app=gate-app -f

```

---

## 🏗️ 인프라 구축 (Infrastructure)

로컬 Kind 클러스터에 필요한 미들웨어를 설치합니다.

```bash
# 1. 네임스페이스 생성
kubectl create namespace rediclaim

# 2. 인프라 배포 (Redis, Kafka, MySQL)
kubectl apply -f k8s/redis.yml
kubectl apply -f k8s/kafka.yml
kubectl apply -f k8s/mysql.yml

```

> **Note:** Redis는 성능 테스트를 위해 `appendonly no`, `save ""` 옵션으로 설정될 수 있습니다. (운영 환경에서는 Persistence 설정 필요)

---

## 🧪 테스트 (Load Testing)

**K6**를 사용하여 대기열 시스템의 성능과 안정성을 검증합니다.

### Gate 모듈 통합 테스트 (`k6-gate-polling.js`)

유저가 `대기열 진입` -> `순번 대기(Polling)` -> `처리열 이동(Processing)` 하는 전체 과정을 검증합니다.

```bash
# 대시보드와 함께 실행
K6_WEB_DASHBOARD=true k6 run scripts/k6-gate-polling.js

```

**주요 검증 항목:**

* [x] **기능 정합성:** 모든 유저가 에러 없이 Processing 상태로 전환되는가?
* [x] **대기 시간(UX):** 유저가 실제 입장하기까지 걸리는 시간 (`waiting_time` 지표)
* [x] **안정성:** 스파이크 트래픽 발생 시 서버가 다운되지 않는가?

---

## 🛠️ Tech Stack

* **Language:** Java 17
* **Framework:** Spring Boot 3.4.4
* **Database:** MySQL 8.0, Redis 7.x
* **Message Queue:** Apache Kafka 3.8
* **Build Tool:** Gradle
* **Deployment:** Docker, Kubernetes (Kind)
* **Testing:** K6 (Load Testing), JUnit 5