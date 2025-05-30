import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const PREALLOCATED_VUS = 5000;      // 사전 예약된 VU 수
const USER_COUNT= 50000;             // 생성할 유저 수
const QUANTITY = 3000;               // 생성할 쿠폰의 수량

export const options = {
    setupTimeout: '180s',       // setup 메서드가 실행되는 최대 대기 시간
    scenarios: {
        issue_flow: {
            executor: 'constant-arrival-rate',      // 초당 고정 RPS
            rate: 1000,                             // 1초당 ,, 회
            timeUnit: '1s',
            duration: '120s',                       // 총 ,, 초간
            preAllocatedVUs: PREALLOCATED_VUS,
            maxVUs: 20000,
        },
    },
    thresholds: {
        // 비즈니스 요구사항
        'coupon_success_count':      ['count==3000'],  // 쿠폰 수량만큼만 성공

        // HTTP SLA (원하면 추가)
        // http_req_failed: ['rate<0.01'],
        // http_req_duration: ['p(95)<500'],
    },
};

const BASE_URL = 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// Counters
export let successCount = new Counter('coupon_success_count');
export let duplicateErrorCount = new Counter('duplicate_error_count');
export let outOfStockErrorCount = new Counter('out_of_stock_error_count');
export let serverErrorCount = new Counter('server_error_count');
export let networkErrorCount = new Counter('network_error_count');

export function setup() {
    const userIds = [];

    // 1) 일반 유저 생성 (배치 요청)
    const batchSize = 2000;
    let batchRequests = [];
    for (let i = 1; i <= USER_COUNT; i++) {
        batchRequests.push([
            'POST',
            `${BASE_URL}/api/users`,
            JSON.stringify({ name: `user_${i}` }),
            { headers: JSON_HEADERS }
        ]);

        if (batchRequests.length === batchSize) {
            const responses = http.batch(batchRequests);
            responses.forEach(r => {
                check(r, { 'create user OK': res => res.status === 200 });
                userIds.push(r.json().result.userId);
            });
            batchRequests = [];
        }
    }
    // 남은 요청 처리
    if (batchRequests.length) {
        const responses = http.batch(batchRequests);
        responses.forEach(r => {
            check(r, { 'create user OK': res => res.status === 200 });
            userIds.push(r.json().result.userId);
        });
    }

    // 2) 쿠폰 생성자 생성
    const creatorRes = http.post(
        `${BASE_URL}/api/creators`,
        JSON.stringify({ name: 'creator_1' }),
        { headers: JSON_HEADERS }
    );
    check(creatorRes, { 'create creator OK': r => r.status === 200 });
    const creatorId = creatorRes.json().result.creatorId;

    // 3) 쿠폰 1개 생성
    const couponRes = http.post(
        `${BASE_URL}/api/coupons`,
        JSON.stringify({
            creatorId:  creatorId,
            quantity:   QUANTITY,
            couponName: 'load-test-coupon',
        }),
        { headers: JSON_HEADERS }
    );
    check(couponRes, { 'create coupon OK': r => r.status === 200 });
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

    // 1) 네트워크 에러
    if (res.error_code) {
        networkErrorCount.add(1);
        return;
    }

    // 2) 응답 JSON 파싱
    let body = {};
    try {
        body = res.json();
    } catch {}

    // 3) 클라이언트 오류 (400)
    if (res.status === 400) {
        if (body.message === '이미 발급받은 쿠폰입니다.') {
            duplicateErrorCount.add(1);
        }
        else if (body.message === '쿠폰 재고가 부족합니다.') {
            outOfStockErrorCount.add(1);
        }

        return;
    }

    // 4) 서버 오류 (5xx)
    if (res.status >= 500) {
        serverErrorCount.add(1);
        return;
    }

    // 5) 성공 (200)
    if (res.status === 200) {
        successCount.add(1);
    }
}

export function teardown(data) {        // 부하테스트 이후 실행
    const { couponId } = data;
    // Redis에 쌓인 쿠폰 발금 요청/완료 로그를 조회하는 검증 전용 api 호출
    const res = http.get(`${BASE_URL}/api/coupons/${couponId}/verification-logs`);
    check(res, { 'got verification logs': r => r.status === 200 });

    const body = res.json();
    const result = body.result;
    if (!result) {
        throw new Error('verification-logs API에 result가 없습니다');
    }

    const completions = result.completions || [];

    // 성공 로그가 요청 순번에 대하여 오름차순 정렬(1, 2, ,,, N)인지 확인 -> 이래야 선착순 쿠폰 발급이 보장되는 것임
    const sequences = completions.map(r => r.requestSequence);
    const N = QUANTITY;
    const isSequential = sequences.length === N && sequences.every((v, i) => v === i + 1);

    console.log(`Completion sequences : ${JSON.stringify(sequences)}`);
    console.log(`Is true 선착순 쿠폰 발급? :  ${isSequential}`);

    check(isSequential, {
        [`completion sequences are 1..${N}`]: v => v
    });
}
