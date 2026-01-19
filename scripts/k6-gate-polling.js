import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics'; // 커스텀 메트릭 사용

// 대기 시간을 측정하기 위한 커스텀 메트릭
const waitingTime = new Trend('waiting_time');

export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '20s', target: 1000 }, // 램프업
    { duration: '1m', target: 2000 },  // 유지
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    // '실제 대기 시간'이 60초 이내
    'waiting_time': ['p(95)<60000'],

    // 최종 입장 성공률 99% 이상
    'checks': ['rate>0.99'],
  },
};

const BASE_URL = 'http://127.0.0.1:8000';
const EVENT_ID = 1001;

export default function () {
  const userId = Math.floor(Math.random() * 1000000) + 1;

  // 1. 대기열 진입 (Enqueue)
  const enqueueStart = Date.now(); // 시간 측정 시작
  let enqRes = http.post(
    `${BASE_URL}/gate/events/${EVENT_ID}/enqueue?userId=${userId}`,
    null,
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'Enqueue' } }
  );

  // 진입 실패 시 종료 (Fail 카운트됨)
  if (!check(enqRes, { 'Enqueue Status 200': (r) => r.status === 200 })) {
    return;
  }

  // 2. 상태 확인 (Polling) - "내 차례 됐나요?"
  let myTurn = false;
  let attempts = 0;
  const maxAttempts = 40; // 최대 120초 대기

  while (!myTurn && attempts < maxAttempts) {
    sleep(3); // 3초 대기

    let pollRes = http.get(
      `${BASE_URL}/gate/events/${EVENT_ID}/rank?userId=${userId}`,
      { tags: { name: 'PollStatus', poll_status: 'true' } }
    );

    // 응답 본문에 PROCESSING이 있으면 입장 성공한 것임!
    // (Gate가 Kafka로 메시지를 쏘고 Processing 상태로 변경했음을 의미)
    if (pollRes.status === 200) {
      const body = pollRes.json();
      const status = body.status; // GateStatusResponse의 status 필드

      // 상태가 PROCESSING이면 입장 성공! (Gate -> Kafka 전송 완료)
      if (status === 'PROCESSING') {
        myTurn = true;

        // [핵심] 입장 성공 시점의 시간 차이를 기록!
        const duration = Date.now() - enqueueStart;
        waitingTime.add(duration);
      }
      // 상태가 WAITING이면 body.rank 에 현재 대기 순번이 있음 (로그 확인 가능)
    }

    attempts++;
  }

  // 3. 최종 검증
  check(myTurn, {
    'Entered Processing successfully': (success) => success === true,
  });
}
