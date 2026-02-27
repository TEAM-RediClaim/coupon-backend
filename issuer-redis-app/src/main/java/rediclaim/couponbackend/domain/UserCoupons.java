package rediclaim.couponbackend.domain;

import java.util.List;

public class UserCoupons {

    private final List<UserCoupon> userCoupons;

    private UserCoupons(List<UserCoupon> userCoupons) {
        this.userCoupons = userCoupons;
    }

    public static UserCoupons of(List<UserCoupon> userCoupons) {
        return new UserCoupons(userCoupons);
    }

    public boolean hasCoupon(Long couponId) {
        return userCoupons.stream().anyMatch(uc -> uc.getCoupon().isSameCoupon(couponId));
    }

    public List<Coupon> getCoupons() {
        return userCoupons.stream()
                .map(UserCoupon::getCoupon)
                .toList();
    }
}