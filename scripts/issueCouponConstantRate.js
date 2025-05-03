import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    scenarios: {
        issue_flow: {
            executor: 'constant-arrival-rate', // 초당 고정 RPS
            rate: 10,                          // 1초당 50회
            timeUnit: '1s',
            duration: '30s',                   // 총 30초간
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
    thresholds: {
        // 비즈니스 요구사항
        'coupon_success_count':      ['count==30'],  // 재고 30개만 성공
        'coupon_out_of_stock_count': ['count==70'],  // 나머지 70개는 재고부족
        'coupon_duplicate_count':    ['count==0'],   // 중복발급 없음
        'coupon_lock_timeout_count': ['count==0'],   // 락 타임아웃 없음
        // HTTP SLA (원하면 추가)
        // http_req_failed: ['rate<0.01'],
        // http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Counters
export let successCount       = new Counter('coupon_success_count');
export let outOfStockCount    = new Counter('coupon_out_of_stock_count');
export let duplicateCount     = new Counter('coupon_duplicate_count');
export let lockTimeoutCount   = new Counter('coupon_lock_timeout_count');

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

    // JSON 파싱
    let body = {};
    try { body = res.json(); } catch {}

    // 상태별 Counter 증가
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
