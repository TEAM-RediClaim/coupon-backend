package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;
import rediclaim.couponbackend.domain.Coupon;

@Getter
public class IssuedCouponInfo {

    private Long couponId;

    private String couponName;

    @Builder
    private IssuedCouponInfo(Long couponId, String couponName) {
        this.couponId = couponId;
        this.couponName = couponName;
    }

    public static IssuedCouponInfo of(Coupon coupon) {
        return IssuedCouponInfo.builder()
                .couponId(coupon.getId())
                .couponName(coupon.getName())
                .build();
    }
}
