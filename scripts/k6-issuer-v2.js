/**
 * [TASK 2] Redis Lua Script 기반 쿠폰 발급 스파이크 부하 테스트
 *
 * DB 초기화 (유저 10,000명 + Creator 세팅)
 * docker exec -i task2-multi-mysql mysql -uroot -proot rediclaim < scripts/init-load-test.sql
 *
 * Redis issued 키 초기화 ← 핵심 (매 테스트 전 필수)
 * docker exec -it task2-multi-redis redis-cli DEL coupon:issued:2 coupon:stock:2
 *
 * 목적
 * 1. Redis Lua Script 원자적 실행이 DB Lock 병목을 제거하여
 *    TASK 1 대비 TPS 향상 및 레이턴시 감소를 정량적으로 증명
 * 2. 멀티 서버 환경에서 단일 Redis 게이트 + 앱 서버 증설 시
 *    TPS가 선형적으로 향상됨을 확인 (TASK 1 멀티와 비교)
 * 3. [선착순 / 중복 발급 금지 / 재고 초과 발급 금지] 핵심 요구사항 준수 검증
 *
 * 핵심 관측 지표
 * - dropped_iterations : VU 고갈로 인해 아예 실행조차 못한 요청 수
 * - http_req_duration  : p(95) / p(99) 레이턴시 — Redis 처리 시간이 직접 반영됨
 * - issue_success_rate : 서버가 정상 처리(200/400)한 비율
 * - http_req_failed    : 실제 서버 장애(5xx, 네트워크 오류)만 집계 (400 제외)
 *
 * Grafana 병행 확인 항목
 * - hikaricp_connections_pending : TASK 1 대비 적체 해소 확인
 * - jvm_threads_states_threads   : Tomcat 스레드 여유 확인
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 400(재고 소진, 중복 발급)을 http_req_failed 집계에서 제외.
// http_req_failed 는 실제 서버 장애(5xx)와 네트워크 오류(status 0)만 반영한다.
http.setResponseCallback(http.expectedStatuses(200, 400));

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const issueSuccessRate = new Rate('issue_success_rate');  // 200 또는 400 응답 비율
const issueDuration    = new Trend('issue_duration_ms', true);

// ─── 환경 변수 ────────────────────────────────────────────────────────────────
const BASE_URL     = __ENV.BASE_URL     || 'http://localhost:8080';
const COUPON_STOCK = __ENV.COUPON_STOCK || 1000;  // 30초 안에 소진될 수 있도록 설정
const USER_COUNT   = __ENV.USER_COUNT   || 10000;  // init-load-test.sql 로 세팅된 유저 수
const MIN_USER_ID  = __ENV.MIN_USER_ID  || 2;     // SQL 세팅 시 NORMAL 유저 시작 id (id=1 은 Creator)
const CREATOR_ID   = __ENV.CREATOR_ID   || 1;     // SQL 세팅 시 Creator id

// ─── 부하 시나리오 ─────────────────────────────────────────────────────────────
export const options = {
  setupTimeout: '30s',
  scenarios: {
    // 이벤트 오픈 시점의 폭발적인 트래픽을 모사하는 Arrival Rate 시나리오.
    // TASK 1과 동일한 파라미터로 공정 비교한다.
    spike_test: {
      executor: 'constant-arrival-rate',

      rate: 500,        // 초당 300개 고정 (총 9,000건 진입 시도) — TASK 1과 동일
      timeUnit: '1s',
      duration: '30s',

      preAllocatedVUs: 1000,
      maxVUs: 5000,
    },
  },

  thresholds: {
    // {name:IssueCoupon-V2} 태그로 쿠폰 발급 API 요청만 측정.
    // setup() 의 creator/coupon/user 생성 요청은 집계에서 제외된다.

    // Redis 처리로 TASK 1 대비 레이턴시가 현저히 낮을 것으로 예상.
    'http_req_duration{name:IssueCoupon-V2}': ['p(95)<1000'],
    // 실제 서버 장애(5xx, 네트워크 오류)율이 1% 이하여야 정상.
    'http_req_failed{name:IssueCoupon-V2}':   ['rate<0.01'],
    // 서버가 정상 처리(200/400)한 비율이 99% 이상이어야 함.
    'issue_success_rate':                     ['rate>0.99'],
  },
};

// ─── 사전 데이터 세팅 (테스트 시작 전 1회 실행) ──────────────────────────────
//
// 전제: init-load-test.sql 이 미리 실행되어 있어야 한다.
//   - users    : id=1 (CREATOR), id=MIN_USER_ID ~ MIN_USER_ID+USER_COUNT-1 (NORMAL)
//   - Creator/유저 생성 API 호출 불필요 → SQL 세팅 값을 그대로 사용
//
// 쿠폰만 API로 생성하는 이유:
//   createCoupon() 내부에서 Redis coupon:stock:{id} 키를 초기화하므로,
//   SQL로 직접 INSERT하면 Redis 키가 없어 Lua 스크립트가 -2(COUPON_NOT_FOUND)를 반환한다.
export function setup() {
  const headers = { 'Content-Type': 'application/json' };

  // 1. 쿠폰 생성 (Redis coupon:stock:{couponId} 키 자동 초기화)
  const couponRes = http.post(
    `${BASE_URL}/api/coupons`,
    JSON.stringify({ creatorId: Number(CREATOR_ID), couponName: 'TASK2-SpikeTest-Coupon', quantity: Number(COUPON_STOCK) }),
    { headers }
  );
  if (couponRes.status !== 200) {
    throw new Error(`쿠폰 생성 실패: ${couponRes.body}`);
  }
  const couponId = couponRes.json('result.couponId');

  // 2. SQL로 세팅된 유저 ID 범위를 배열로 구성 (API 호출 없음)
  const totalUsers = Number(USER_COUNT);
  const startId    = Number(MIN_USER_ID);
  const userIds    = Array.from({ length: totalUsers }, (_, i) => startId + i);

  return { couponId, userIds };
}

// ─── 메인 테스트 로직 ─────────────────────────────────────────────────────────
export default function (data) {
  const { couponId, userIds } = data;
  const userId = userIds[Math.floor(Math.random() * userIds.length)];

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/coupons/${couponId}`,
    JSON.stringify({ userId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags:    { name: 'IssueCoupon-V2' },
      timeout: '30s',
    }
  );
  const elapsed = Date.now() - start;
  issueDuration.add(elapsed);

  // 200 : 쿠폰 발급 성공 (Redis 게이트 통과 + DB 저장 완료)
  // 400 : 재고 소진 또는 중복 발급 — Redis가 즉시 거절한 정상 비즈니스 예외
  // 그 외 : 서버 오류 또는 네트워크 장애
  const processed = res.status === 200 || res.status === 400;
  issueSuccessRate.add(processed);

  check(res, {
    'server processed request (200 or 400)': (r) => r.status === 200 || r.status === 400,
  });
}

// ─── 테스트 종료 후 요약 ──────────────────────────────────────────────────────
export function teardown(data) {
  console.log('=== TASK 2 스파이크 부하 테스트 완료 ===');
  console.log('결과 해석 가이드:');
  console.log('  TASK 1 대비 dropped_iterations 감소  → 스레드 고갈 해소: Redis가 빠르게 처리해 스레드 해방');
  console.log('  TASK 1 대비 http_req_duration 감소   → Lock 제거: Redis 응답이 DB Lock 대기 제거');
  console.log('  issue_success_rate 높음              → 처리율 향상: 커넥션 적체 없이 요청 정상 처리');
  console.log('Grafana 병행 확인: hikaricp_connections_pending (TASK 1 대비 감소), jvm_threads_states_threads');
}