package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VerificationLogsResponse {

    private boolean firstComeFirstServe;
}
