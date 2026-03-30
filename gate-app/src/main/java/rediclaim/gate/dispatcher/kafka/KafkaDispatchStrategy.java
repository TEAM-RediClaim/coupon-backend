package rediclaim.gate.dispatcher.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.controller.dto.GateStatusResponse;
import rediclaim.gate.dispatcher.DispatchStrategy;
import rediclaim.gate.repository.GateRedisRepository;

import java.util.List;

/**
 * {@code gate.dispatch-mode=kafka} 전략
 *
 * <p>대기열에서 N명을 꺼내 Processing Queue 로 이동한 뒤 Kafka 에 발행한다.
 * issuer-worker-app 이 메시지를 소비하여 쿠폰을 발급하고,
 * 발급 완료 후 gate-app 의 콜백 엔드포인트({@code /gate/events/{eventId}/processing/complete})를
 * 호출해 Processing Queue 에서 유저를 제거한다.</p>
 */
@Slf4j
@ConditionalOnProperty(name = "gate.dispatch-mode", havingValue = "kafka")
@Service
@RequiredArgsConstructor
public class KafkaDispatchStrategy implements DispatchStrategy {

    private final GateRedisRepository gateRedisRepository;
    private final KafkaTemplate<String, IssueRequestMessage> kafkaTemplate;
    private final GateProperties gateProperties;

    @Override
    public int dispatch(Long eventId) {
        int rate = gateProperties.getDispatchQuantity();
        if (rate <= 0) return 0;

        // [userId, ticket, userId, ticket ...] 형태로 반환
        List<String> rawList = gateRedisRepository.popToProcessing(eventId, rate);
        if (rawList.isEmpty()) return 0;

        String topic = gateProperties.getKafkaTopic();
        int count = 0;

        for (int i = 0; i < rawList.size(); i += 2) {
            Long userId = Long.parseLong(rawList.get(i));
            long ticket = (long) Double.parseDouble(rawList.get(i + 1));

            kafkaTemplate.send(topic, eventId.toString(), new IssueRequestMessage(eventId, userId, ticket));
            log.debug("Event {} userId {} ticket {} sent to Kafka topic {}", eventId, userId, ticket, topic);
            count++;
        }

        return count;
    }

    @Override
    public GateStatusResponse statusOf(Long eventId, Long userId) {
        if (gateRedisRepository.isProcessing(eventId, userId)) {
            return new GateStatusResponse("PROCESSING", null);
        }
        return new GateStatusResponse("UNKNOWN", null);
    }

    @Override
    public int requeueStale(Long eventId) {
        return gateRedisRepository.requeueStaleProcessing(
                eventId,
                gateProperties.getStaleTimeoutMs(),
                gateProperties.getMaxRequeue()
        );
    }
}