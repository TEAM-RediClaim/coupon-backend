package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.service.EventPublisher;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {
    
    private final EventPublisher eventPublisher;
    
    @PostMapping("/send")
    public String sendEvent(@RequestParam String message) {
        eventPublisher.publishEvent(message);
        return "이벤트 발송 완료: " + message;
    }
} 
