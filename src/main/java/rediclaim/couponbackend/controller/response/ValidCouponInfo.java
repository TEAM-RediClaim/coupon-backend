package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;
import rediclaim.couponbackend.domain.Coupon;

@Getter
public class ValidCouponInfo {

    private Long couponId;

    private int remainingCount;

    @Builder
    private ValidCouponInfo(Long couponId, int remainingCount) {
        this.couponId = couponId;
        this.remainingCount = remainingCount;
    }

    public static ValidCouponInfo of(Coupon coupon) {
        return ValidCouponInfo.builder()
                .couponId(coupon.getId())
                .remainingCount(coupon.getRemainingCount())
                .build();
    }
}
