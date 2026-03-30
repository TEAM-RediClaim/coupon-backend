package rediclaim.gate.dispatcher.activequeue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.controller.dto.GateStatusResponse;
import rediclaim.gate.dispatcher.DispatchStrategy;
import rediclaim.gate.repository.GateRedisRepository;

import java.util.List;

/**
 * {@code gate.dispatch-mode=active-queue} 전략
 *
 * <p>대기열에서 N명을 꺼내 Active Queue(TTL Key)에 올려둔다.
 * 클라이언트는 상태 폴링을 통해 ACTIVE 상태를 확인한 후
 * issuer-api-app 에 쿠폰 발급을 직접 요청한다.</p>
 *
 * <p>gate-app 은 Kafka 발행 없이 Active Queue 적재만 담당하며,
 * issuer-api-app 이 Active Key 존재 여부로 발급 허용 여부를 판단한다.</p>
 */
@Slf4j
@ConditionalOnProperty(name = "gate.dispatch-mode", havingValue = "active-queue")
@Service
@RequiredArgsConstructor
public class ActiveQueueDispatchStrategy implements DispatchStrategy {

    private final GateRedisRepository gateRedisRepository;
    private final GateProperties gateProperties;

    @Override
    public int dispatch(Long eventId) {
        int rate = gateProperties.getDispatchQuantity();
        if (rate <= 0) return 0;

        List<Long> userIds = gateRedisRepository.popToActive(eventId, rate, gateProperties.getActiveTtlSeconds());
        if (!userIds.isEmpty()) {
            log.debug("Event {} dispatched {} users to active queue", eventId, userIds.size());
        }
        return userIds.size();
    }

    @Override
    public GateStatusResponse statusOf(Long eventId, Long userId) {
        if (gateRedisRepository.isActive(eventId, userId)) {
            return new GateStatusResponse("ACTIVE", null);
        }
        return new GateStatusResponse("UNKNOWN", null);
    }
}