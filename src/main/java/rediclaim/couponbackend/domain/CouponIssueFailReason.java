package rediclaim.couponbackend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum CouponIssueFailReason {
    DUPLICATE("duplicate"),
    OUT_OF_STOCK("out_of_stock");

    private final String value;
}
