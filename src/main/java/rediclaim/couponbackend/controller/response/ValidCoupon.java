package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;
import rediclaim.couponbackend.domain.Coupon;

@Getter
public class ValidCoupon {

    private Long couponId;

    private int remainingCount;

    @Builder
    private ValidCoupon(Long couponId, int remainingCount) {
        this.couponId = couponId;
        this.remainingCount = remainingCount;
    }

    public static ValidCoupon of(Coupon coupon) {
        return ValidCoupon.builder()
                .couponId(coupon.getId())
                .remainingCount(coupon.getRemainingCount())
                .build();
    }
}
