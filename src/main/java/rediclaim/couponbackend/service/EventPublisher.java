package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import rediclaim.couponbackend.domain.event.SimpleEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "simple-events";
    
    public void publishEvent(String message) {
        SimpleEvent event = new SimpleEvent(message);
        
        log.info("이벤트 발송: {}", event);
        kafkaTemplate.send(TOPIC, event.getId(), event);
    }
} 
