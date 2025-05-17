import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    scenarios: {
        issue_flow: {
            executor: 'constant-arrival-rate', // 초당 고정 RPS
            rate: 100,                          // 1초당 ,, 회
            timeUnit: '1s',
            duration: '120s',                   // 총 ,, 초간
            preAllocatedVUs: 500,
            maxVUs: 500,
        },
    },
    thresholds: {
        // 비즈니스 요구사항
        'coupon_success_count':      ['count==30'],  // 재고 30개만 성공
        'coupon_duplicate_count':    ['count==0'],   // 중복발급 없음

        // HTTP SLA (원하면 추가)
        // http_req_failed: ['rate<0.01'],
        // http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Counters
export let successCount       = new Counter('coupon_success_count');
export let duplicateCount     = new Counter('coupon_duplicate_count');
export let networkErrorCount  = new Counter('network_error_count');

export function setup() {
    const userIds = [];

    // 1) 일반 유저 100명 생성
    for (let i = 1; i <= 100; i++) {
        const res = http.post(
            `${BASE_URL}/api/users`,
            JSON.stringify({ name: `user_${i}` }),
            { headers: JSON_HEADERS }
        );
        check(res, { 'create user OK': (r) => r.status === 200 });
        userIds.push(res.json().result.userId);
    }

    // 2) 쿠폰 생성자 1명
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
            creatorId:  creatorId,
            quantity:   30,
            couponName: 'load-test-coupon',
        }),
        { headers: JSON_HEADERS }
    );
    check(couponRes, { 'create coupon OK': (r) => r.status === 200 });
    const couponId = couponRes.json().result.couponId;

    return { userIds, couponId };
}

export default function (data) {
    const { userIds, couponId } = data;
    const userId = randomItem(userIds);

    const res = http.post(
        `${BASE_URL}/api/coupons/${couponId}`,
        JSON.stringify({ userId }),
        { headers: JSON_HEADERS }
    );

    // 1) 네트워크 에러 (DNS, 타임아웃 등)
    if (res.error_code) {
        networkErrorCount.add(1, { error: res.error_code });
        return;
    }

    // 2) 비즈니스 에러 (4xx)
    let body = {};
    try { body = res.json(); } catch {}

    if (res.status === 400) {
        if (body.message === '이미 발급받은 쿠폰입니다.') {
            duplicateCount.add(1);
        }
        return;
    }

    // 3) 성공
    if (res.status === 200) {
        successCount.add(1);
    }
}
