package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LogRecord {

    private Long userId;

    private String timestamp;

    private Long requestSequence;
}
