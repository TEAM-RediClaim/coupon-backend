package rediclaim.couponbackend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import rediclaim.couponbackend.domain.event.SimpleEvent;

@Slf4j
@Service
public class EventConsumer {
    
    @KafkaListener(topics = "simple-events")
    public void handleEvent(SimpleEvent event) {
        log.info("이벤트 수신: {}", event);
        processEvent(event);
    }
    
    private void processEvent(SimpleEvent event) {
        log.info("이벤트 처리 완료: ID={}, Message={}", event.getId(), event.getMessage());
    }
} 
