package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class VerificationLogsResponse {

    private List<LogRecord> completions;
}
