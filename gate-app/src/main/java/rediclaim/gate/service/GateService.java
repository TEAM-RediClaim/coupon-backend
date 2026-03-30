package rediclaim.gate.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rediclaim.gate.dispatcher.DispatchStrategy;
import rediclaim.gate.repository.GateEnqueueDto;
import rediclaim.gate.repository.GateRedisRepository;
import rediclaim.gate.controller.dto.GateEnqueueResponse;
import rediclaim.gate.controller.dto.GateStatusResponse;

@Service
@RequiredArgsConstructor
public class GateService {

    private final GateRedisRepository gateRedisRepository;
    private final DispatchStrategy dispatchStrategy;

    public GateEnqueueResponse enqueue(Long eventId, Long userId) {
        GateEnqueueDto result = gateRedisRepository.enqueueLua(eventId, userId);

        if (result.enqueued()) {
            return new GateEnqueueResponse("ENQUEUED", result.rank() + 1);
        } else {
            return new GateEnqueueResponse("ALREADY_ENQUEUED", result.rank() + 1);
        }
    }

    public GateStatusResponse getStatus(Long eventId, Long userId) {
        // 1. 대기열 확인
        Long rank = gateRedisRepository.getRank(eventId, userId).orElse(null);
        if (rank != null) {
            return new GateStatusResponse("WAITING", rank + 1);
        }

        // 2. dispatch-mode 별 상태 확인 (PROCESSING / ACTIVE / UNKNOWN)
        return dispatchStrategy.statusOf(eventId, userId);
    }

    /**
     * 대기열에서 N명을 꺼내 다음 단계로 이동.
     * - kafka 모드      : Processing Queue 이동 후 Kafka 발행
     * - active-queue 모드 : Active Queue(TTL) 이동 후 클라이언트가 issuer-api-app 직접 호출
     */
    public int dispatchOnce(Long eventId) {
        return dispatchStrategy.dispatch(eventId);
    }

    /**
     * issuer-worker-app 의 처리 완료 콜백을 받아 processing 에서 제거.
     * kafka 모드에서만 호출된다.
     */
    public void removeFromProcessing(Long eventId, Long userId) {
        gateRedisRepository.removeFromProcessing(eventId, userId);
    }

    /**
     * processing 에 오래 머무른 요청을 queue 로 되돌림.
     * kafka 모드에서만 실질적인 동작을 수행하며, active-queue 모드에서는 no-op이다.
     */
    public int requeueStaleProcessing(Long eventId) {
        return dispatchStrategy.requeueStale(eventId);
    }
}