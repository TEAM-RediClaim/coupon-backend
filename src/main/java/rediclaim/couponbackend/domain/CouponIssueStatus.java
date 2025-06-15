package rediclaim.couponbackend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum CouponIssueStatus {
    SUCCESS("SUCCESS"),
    FAIL("FAIL");

    private final String value;
}
