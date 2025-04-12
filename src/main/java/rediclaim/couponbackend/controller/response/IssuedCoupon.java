package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;
import rediclaim.couponbackend.domain.Coupon;

@Getter
public class IssuedCoupon {

    private Long couponId;

    private String couponName;

    @Builder
    private IssuedCoupon(Long couponId, String couponName) {
        this.couponId = couponId;
        this.couponName = couponName;
    }

    public static IssuedCoupon of(Coupon coupon) {
        return IssuedCoupon.builder()
                .couponId(coupon.getId())
                .couponName(coupon.getName())
                .build();
    }
}
