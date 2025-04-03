package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.IssuedCouponInfo;
import rediclaim.couponbackend.controller.response.IssuedCoupons;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.UserCoupons;
import rediclaim.couponbackend.repository.UserCouponRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserCouponRepository userCouponRepository;

    public IssuedCoupons showAllIssuedCoupons(Long userId) {
        UserCoupons userCoupons = UserCoupons.of(userCouponRepository.findByUserId(userId));
        List<Coupon> coupons = userCoupons.getCoupons();
        List<IssuedCouponInfo> list = coupons.stream()
                .map(IssuedCouponInfo::of)
                .toList();

        return IssuedCoupons.builder()
                .issuedCouponInfos(list)
                .build();
    }
}
