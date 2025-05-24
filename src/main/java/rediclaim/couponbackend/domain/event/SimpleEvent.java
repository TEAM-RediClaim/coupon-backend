package rediclaim.couponbackend.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleEvent {
    private String id;
    private String message;
    private LocalDateTime timestamp;
    
    public SimpleEvent(String message) {
        this.id = java.util.UUID.randomUUID().toString();
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
