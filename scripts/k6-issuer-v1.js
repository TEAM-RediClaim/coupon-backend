/**
 * [TASK 1] 비관적 락 기반 쿠폰 발급 스파이크 부하 테스트 (단일 / 멀티 서버 모두 활용)
 *
 * 전제 조건:
 *   k6 실행 전 init-load-test.sql 로 DB 데이터를 세팅해야 한다.
 *
 *   docker exec -i task1-multi-mysql mysql -uroot -proot rediclaim < scripts/init-load-test.sql
 *
 * 실행:
 *   BASE_URL=http://localhost:8080 k6 run scripts/k6-issuer-multi.js
 *
 * k6-issuer-v1.js 와의 차이:
 *   - setup() 에서 유저/쿠폰 데이터를 생성하지 않음
 *     → 멀티 서버 환경에서 setup() 의 대량 INSERT 가 60초 제한을 초과하는 문제 해결
 *   - COUPON_ID, MIN_USER_ID, USER_COUNT 환경변수로 사전 세팅된 데이터를 참조
 *
 * 핵심 관측 지표 (k6-issuer-v1.js 와 동일)
 *   - http_req_duration{name:IssueCoupon-V1} p(95) : DB Lock 대기 레이턴시
 *   - http_req_failed{name:IssueCoupon-V1}        : 서버 장애율 (5xx, 네트워크 오류)
 *   - issue_success_rate                          : 서버 정상 처리율 (200/400)
 *   - dropped_iterations                          : VU 고갈로 실행 못 한 요청 수
 *
 * Grafana 병행 확인 항목
 *   - hikaricp_connections_pending  : 인스턴스별 커넥션 대기 큐 (3대 각각 확인)
 *   - innodb_row_lock_waits (rate)  : 앱 서버 증가 시 Lock 경쟁 심화 여부
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 400(재고 소진, 중복 발급)을 http_req_failed 집계에서 제외.
http.setResponseCallback(http.expectedStatuses(200, 400));

// ─── 커스텀 메트릭 ────────────────────────────────────────────────────────────
const issueSuccessRate = new Rate('issue_success_rate');
const issueDuration    = new Trend('issue_duration_ms', true);

// ─── 환경 변수 ────────────────────────────────────────────────────────────────
// init-load-test.sql 실행 후 생성되는 데이터 기준 기본값.
//   users: id=1 (CREATOR), id=2~10001 (CUSTOMER 10000명)
//   coupons: id=1
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8080';
const COUPON_ID   = parseInt(__ENV.COUPON_ID   || '1');
const MIN_USER_ID = parseInt(__ENV.MIN_USER_ID || '2');   // creator(id=1) 제외
const USER_COUNT  = parseInt(__ENV.USER_COUNT  || '10000');

// ─── 부하 시나리오 ─────────────────────────────────────────────────────────────
export const options = {
  scenarios: {
    spike_test: {
      executor: 'constant-arrival-rate',

      rate: 300,
      timeUnit: '1s',
      duration: '30s',

      preAllocatedVUs: 1000,
      maxVUs: 5000,
    },
  },

  thresholds: {
    // 단일 서버와 동일한 기준으로 멀티 서버 결과 비교.
    'http_req_duration{name:IssueCoupon-V1}': ['p(95)<2000'],
    'http_req_failed{name:IssueCoupon-V1}':   ['rate<0.01'],
    'issue_success_rate':                     ['rate>0.99'],
  },
};

// ─── 사전 검증 (서버 기동 여부 확인) ─────────────────────────────────────────
export function setup() {
  // actuator/health 로 서버 기동 여부만 확인.
  // 쿠폰/유저 데이터는 init-load-test.sql 로 사전 세팅되어 있다고 가정.
  const res = http.get(`${BASE_URL}/actuator/health`, {
    tags: { name: 'setup-verify' },
  });

  if (res.status !== 200) {
    throw new Error(
      `서버가 응답하지 않습니다 (status=${res.status}). ` +
      `docker compose 가 정상 기동되었는지 확인하세요.`
    );
  }

  console.log(`== 사전 검증 완료 ==`);
  console.log(`  server     : ${BASE_URL} (UP)`);
  console.log(`  couponId   : ${COUPON_ID}`);
  console.log(`  userId 범위: ${MIN_USER_ID} ~ ${MIN_USER_ID + USER_COUNT - 1} (${USER_COUNT}명)`);
  console.log(`  ** init-load-test.sql 실행 여부를 반드시 확인하세요 **`);

  return { couponId: COUPON_ID, minUserId: MIN_USER_ID, userCount: USER_COUNT };
}

// ─── 메인 테스트 로직 ─────────────────────────────────────────────────────────
export default function (data) {
  const { couponId, minUserId, userCount } = data;
  // init-load-test.sql 이 생성한 유저 범위 [minUserId, minUserId + userCount - 1] 에서 랜덤 선택.
  const userId = minUserId + Math.floor(Math.random() * userCount);

  const start = Date.now();
  const res = http.post(
    `${BASE_URL}/api/coupons/${couponId}`,
    JSON.stringify({ userId }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags:    { name: 'IssueCoupon-V1' },
      timeout: '30s',
    }
  );
  issueDuration.add(Date.now() - start);

  const processed = res.status === 200 || res.status === 400;
  issueSuccessRate.add(processed);

  check(res, {
    'server processed request (200 or 400)': (r) => r.status === 200 || r.status === 400,
  });
}

// ─── 테스트 종료 후 요약 ──────────────────────────────────────────────────────
export function teardown(data) {
  console.log('=== TASK 1 멀티 서버 스파이크 부하 테스트 완료 ===');
  console.log('단일 서버(k6-issuer-v1.js) 결과와 비교할 지표:');
  console.log('  dropped_iterations 비교    → VU 고갈: 서버 3대가 Lock 경쟁 심화로 오히려 악화되는지 확인');
  console.log('  http_req_duration p(95) 비교 → 레이턴시: 서버 증가에 따른 개선 여부');
  console.log('  issue_success_rate 비교    → 처리율: TPS 가 서버 수에 비례하는지 확인');
  console.log('Grafana: 인스턴스별 hikaricp_connections_pending, DB innodb_row_lock_waits 비교');
}
