/**
 * [TASK 3] E2E 부하 테스트
 * 대기열 진입 → 폴링(WAITING) → 입장(ACTIVE) → 쿠폰 발급 완료
 *
 * 데이터 세팅 (테스트 실행 전 필수):
 *   docker exec -i task3-mysql mysql -uroot -proot rediclaim < scripts/init-load-test-task3.sql
 *   (coupon id=1, stock=500 / user id=2~2001)
 *
 *   docker exec task3-redis redis-cli FLUSHALL
 *   (redis 초기화)
 *
 * 목적:
 *   1. 2000명 동시 진입에도 gate-app 에러율 0% 유지 (대기열이 폭발적 트래픽 흡수)
 *   2. DispatchScheduler 가 120명/2초 단위로 issuer-api-app 에 순차 전달 (트래픽 셰이핑)
 *   3. 재고(500개) 초과 없이 정확한 선착순 발급 보장
 *      - 2000명 중 선착순 500명 → SUCCESS
 *      - 나머지 1500명         → OUT_OF_STOCK (서버 에러 아님)
 *
 * 핵심 관측 지표:
 *   - enqueue_success_rate : 대기열 진입 성공률 (목표: 100%)
 *   - queue_to_active_ms   : 대기열 진입 → ACTIVE 전환까지 시간
 *                            (dispatch 120명/2초 → 2000명 기준 최대 34초)
 *   - issue_success_count  : 실제 발급 성공 수 (DB 레코드 수와 일치해야 함)
 *   - issue_oos_count      : 재고 소진으로 인한 거절 수
 *
 * 결과 검증 (테스트 완료 후):
 *   SELECT coupon_id, COUNT(*) FROM user_coupon GROUP BY coupon_id;
 *   → coupon_id=1 의 COUNT(*) = issue_success_count 이면 정합성 보장 검증 완료
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import exec from 'k6/execution';

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const enqueueSuccessRate = new Rate('enqueue_success_rate');      // 대기열 진입 성공률
const queueToActiveMs    = new Trend('queue_to_active_ms', true); // 대기열 → ACTIVE 전환 시간(ms)
const issueSuccessCount  = new Counter('issue_success_count');    // 발급 성공 수 (재고와 일치해야 함)
const issueOosCount      = new Counter('issue_oos_count');        // 재고 소진 수

// ─── 환경 변수 ────────────────────────────────────────────────────────────────
const GATE_URL    = __ENV.GATE_URL    || 'http://localhost:8000';
const ISSUER_URL  = __ENV.ISSUER_URL  || 'http://localhost:8082';
const EVENT_ID    = Number(__ENV.EVENT_ID    || 1);
const MIN_USER_ID = Number(__ENV.MIN_USER_ID || 2); // init-load-test.sql 기준 첫 NORMAL 유저 id

// ─── 부하 시나리오 ─────────────────────────────────────────────────────────────
export const options = {
  setupTimeout: '10s',
  scenarios: {
    e2e_flow: {
      executor: 'shared-iterations',
      vus: 2000,        // 재고(500개)의 4배 동시 진입 → 트래픽 셰이핑 효과 명확히 관측
      iterations: 2000, // vus = iterations → 각 VU 1회 실행, 순환 없음
      maxDuration: '8m',
    },
  },
  thresholds: {
    // 대기열 진입은 모든 요청 성공 (gate-app 이 폭발적 트래픽을 에러 없이 수용해야 함)
    'enqueue_success_rate':          ['rate>0.99'],
    // 95% 유저가 60초 이내 ACTIVE 전환 (2000명 / 120명 * 2초 = 34초 + 여유)
    'queue_to_active_ms':            ['p(95)<60000'],
    // 5xx / 네트워크 에러 없음 (OUT_OF_STOCK 은 200 응답이므로 집계 제외)
    'http_req_failed{name:Enqueue}': ['rate<0.01'],
    'http_req_failed{name:Issue}':   ['rate<0.01'],
  },
};

// ─── 사전 헬스 체크 ───────────────────────────────────────────────────────────
export function setup() {
  const gateHealth   = http.get(`${GATE_URL}/actuator/health`);
  const issuerHealth = http.get(`${ISSUER_URL}/actuator/health`);
  if (gateHealth.status   !== 200) throw new Error('gate-app 이 실행되지 않았습니다');
  if (issuerHealth.status !== 200) throw new Error('issuer-api-app 이 실행되지 않았습니다');
}

// ─── 메인 테스트 로직 ─────────────────────────────────────────────────────────
export default function () {

  // iteration 0 → userId=MIN_USER_ID, iteration 1 → MIN_USER_ID+1, ...
  // shared-iterations 환경에서 각 iteration 마다 고유한 userId 보장
  const userId = MIN_USER_ID + exec.scenario.iterationInTest;

  // ── Step 1. 대기열 진입 (Enqueue) ─────────────────────────────────────────
  // 2000 VU 동시 시작으로 인한 초반 burst 분산 (0~5초)
  sleep(Math.random() * 5);

  const enqueueStart = Date.now();
  const enqRes = http.post(
    `${GATE_URL}/gate/events/${EVENT_ID}/enqueue?userId=${userId}`,
    null,
    { tags: { name: 'Enqueue' } }
  );

  const enqOk = check(enqRes, { 'enqueue 200': (r) => r.status === 200 });
  enqueueSuccessRate.add(enqOk);
  if (!enqOk) return;

  // ── Step 2. 폴링: ACTIVE 될 때까지 대기 ──────────────────────────────────
  // DispatchScheduler 가 2초마다 120명씩 ACTIVE 로 전환
  // 2000명 기준: 17 라운드 * 2초 = 34초 내 전원 ACTIVE
  //
  // 폴링 jitter (1~3초): VU 간 폴링 요청이 동시에 몰리지 않도록 분산
  let isActive = false;
  const maxPolls = 25; // 최대 25회 * ~3.5초(avg) = 약 87초 (안전 여유)

  for (let i = 0; i < maxPolls; i++) {
    sleep(2 + Math.random() * 3); // 2~5초 jitter (dispatch 2초 주기 고려)

    const pollRes = http.get(
      `${GATE_URL}/gate/events/${EVENT_ID}/rank?userId=${userId}`,
      { tags: { name: 'Poll' } }
    );

    if (pollRes.status === 200 && pollRes.json('status') === 'ACTIVE') {
      isActive = true;
      queueToActiveMs.add(Date.now() - enqueueStart);
      break;
    }
  }

  // ACTIVE 전환 타임아웃 → 발급 단계 건너뜀
  if (!isActive) return;

  // ── Step 3. 쿠폰 발급 (Issue) ─────────────────────────────────────────────
  // issuer-api-app 은 Active Queue 를 검증한 뒤 DB 재고를 원자적으로 차감
  // gate 가 이미 트래픽을 제어했으므로 별도 jitter 없이 즉시 요청
  const issueRes = http.post(
    `${ISSUER_URL}/issue/events/${EVENT_ID}?userId=${userId}`,
    null,
    { tags: { name: 'Issue' } }
  );

  check(issueRes, { 'issue 200': (r) => r.status === 200 });

  if (issueRes.status === 200) {
    const result = issueRes.json('result');
    // SUCCESS      : 선착순 발급 완료 → DB 레코드 수와 일치해야 함
    // OUT_OF_STOCK : 재고 소진 (서버 에러 아님, 정상 비즈니스 응답)
    if (result === 'SUCCESS')      issueSuccessCount.add(1);
    if (result === 'OUT_OF_STOCK') issueOosCount.add(1);
  }
}

// ─── 테스트 종료 후 요약 ──────────────────────────────────────────────────────
export function teardown() {
  console.log('=== TASK 3 E2E 테스트 완료 ===');
  console.log('DB 정합성 검증:');
  console.log('  SELECT coupon_id, COUNT(*) FROM user_coupon GROUP BY coupon_id;');
  console.log('  → issue_success_count 값과 일치하면 초과 발급 없음 검증 완료');
}
