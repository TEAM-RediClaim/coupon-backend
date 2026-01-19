import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 스파이크 테스트 시나리오 (티켓팅 오픈 상황)
  stages: [
    { duration: '10s', target: 50 },    // [Warm-up] 오픈 10초 전: 슬슬 들어옴
    { duration: '10s', target: 1000 },  // [Spike!] 오픈 정각: 10초 만에 1000명 동시 접속 폭발!
    { duration: '1m', target: 2000 },   // [Sustain] 1분간 전쟁터 유지 (Redis에 쌓이는지 확인)
    { duration: '30s', target: 0 },     // [Cool-down] 매진 후 퇴장
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],    // 에러율 1% 미만이어야 합격 (가장 중요)
    http_req_duration: ['p(95)<2000'], // 95% 유저는 2초 안에 '대기열 진입' 응답을 받아야 함
  },
};

const BASE_URL = 'http://127.0.0.1:8000';
const EVENT_ID = 1001;

export default function () {
  // 1. 랜덤 유저 ID (1 ~ 100만)
  const userId = Math.floor(Math.random() * 1000000) + 1;

  // 2. Gate 진입 요청 (Enqueue)
  const res = http.post(
    `${BASE_URL}/gate/events/${EVENT_ID}/enqueue?userId=${userId}`,
    null,
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'EnqueueReq' }
    }
  );

  const isStatus200 = res.status === 200;
  const isEnqueued = isStatus200 && res.body && (res.body.includes('ENQUEUED') || res.body.includes('ALREADY'));

  // 3. 검증
  check(res, {
    'status is 200': (r) => isStatus200,
    'body has ENQUEUED': (r) => isEnqueued,
  });

  // 4. 매우 짧은 대기 (광클 시뮬레이션)
  sleep(0.05);
}
