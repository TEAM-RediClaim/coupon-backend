package rediclaim.gate.dispatcher.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.dispatcher.DispatchUserDto;
import rediclaim.gate.dispatcher.RequestDispatcher;

import java.util.List;

@Slf4j
@ConditionalOnProperty(
        name = "gate.dispatch-mode",
        havingValue = "kafka",
        matchIfMissing = true       // 디폴트 설정
)
@Service
@RequiredArgsConstructor
public class RequestKafkaDispatcher implements RequestDispatcher {

    private final KafkaTemplate<String, IssueRequestMessage> kafkaTemplate;
    private final GateProperties gateProperties;

    @Override
    public void dispatchBatch(Long eventId, List<DispatchUserDto> dtos) {
        String topic = gateProperties.getKafkaTopic();

        for (DispatchUserDto dto : dtos) {
            IssueRequestMessage message = new IssueRequestMessage(eventId, dto.userId(), dto.rank());

            // 파티션 키를 eventId로 설정
            // 동일 eventId는 같은 파티션으로 전송되므로 순서 보장
            kafkaTemplate.send(topic, eventId.toString(), message);

            log.debug("Event {} userId {} sent to Kafka topic {}", eventId, dto.userId(), topic);
        }
    }
}
