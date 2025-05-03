import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

/**
 * 동시에 100명의 유저가 쿠폰을 발급하는 상황 재현
 */

export const options = {
    scenarios: {
        issue_flow: {
            executor: 'per-vu-iterations',  // VU 당 정해진 iteration 횟수만큼 바로 실행
            vus: 100,                       // 100개의 가상 사용자
            iterations: 1,                  // 각 VU가 1회씩만 default() 실행
            maxDuration: '1m',              // 안전 타임아웃
        },
    },
    thresholds: {
        'coupon_success_count':         ['count==30'],  // 재고 수량
        'coupon_out_of_stock_count':    ['count==70'],  // 나머지는 재고 부족
        'coupon_duplicate_count':       ['count==0'],   // 중복 발급 없음
        'coupon_lock_timeout_count':    ['count==0'],   // 잠금 타임아웃 없음
        // http_req_failed: ['rate<0.01'],
        // http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export let successCount = new Counter('coupon_success_count');      // 쿠폰 발급 성공 카운트
export let outOfStockCount = new Counter('coupon_out_of_stock_count');      // 재고 부족 에러 카운트
export let duplicateCount = new Counter('coupon_duplicate_count');      // 중복 발급 에러 카운트
export let lockTimeoutCount = new Counter('coupon_lock_timeout_count');     // 락 타임아웃 에러 카운트

export function setup() {
    const userIds = [];

    // 1) 일반 유저 100명 생성
    for (let i = 1; i <= 100; i++) {
        const res = http.post(
            `${BASE_URL}/api/users`,
             JSON.stringify({name: `user_${i}`}),
            { headers: JSON_HEADERS }
        );
        check(res, { 'create user OK': (r) => r.status === 200 });
        userIds.push(res.json().result.userId);
    }

    // 2) 쿠폰 생성자 1명 등록
    const creatorRes = http.post(
        `${BASE_URL}/api/creators`,
        JSON.stringify({ name: 'creator_1' }),
        { headers: JSON_HEADERS }
    );
    check(creatorRes, { 'create creator OK': (r) => r.status === 200 });
    const creatorId = creatorRes.json().result.creatorId;

    // 3) 재고 30개짜리 쿠폰 1개 생성
    const couponRes = http.post(
        `${BASE_URL}/api/coupons`,
        JSON.stringify({
            creatorId: creatorId,
            quantity: 30,
            couponName: 'load-test-coupon',
        }),
        { headers: JSON_HEADERS }
    );
    check(couponRes, { 'create coupon OK': (r) => r.status === 200 });
    const couponId = couponRes.json().result.couponId;

    // default()로 전달
    return { userIds, couponId };
}

export default function (data) {
    const { userIds, couponId } = data;
    // __VU 는 1부터 시작하는 VU 번호
    const userId   = userIds[__VU - 1];

    const res  = http.post(
        `${BASE_URL}/api/coupons/${couponId}`,
        JSON.stringify({ userId }),
        { headers: JSON_HEADERS }
    );

    let body = {};
    try { body = res.json(); } catch {}

    if (res.status === 200) {
        successCount.add(1);

    } else if (res.status === 400) {
        if (body.message === '쿠폰 재고가 부족합니다.') {
            outOfStockCount.add(1);
        }
        else if (body.message === '이미 발급받은 쿠폰입니다.') {
            duplicateCount.add(1);
        }

    } else if (res.status === 500
        && body.message === '쿠폰 LOCK 획득 대기 시간이 초과되었습니다.') {
        lockTimeoutCount.add(1);
    }
}
