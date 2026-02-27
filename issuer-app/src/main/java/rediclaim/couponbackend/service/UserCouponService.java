package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.IssuedCoupon;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.UserCoupons;
import rediclaim.couponbackend.repository.UserCouponRepository;

import java.util.List;

/**
 * 유저별 발급 쿠폰 조회 서비스
 *
 * <p>{@link UserService}(user-module)는 User 등록만 담당한다.
 * Coupon·UserCoupon 도메인을 참조하는 발급 쿠폰 조회는 이 클래스에 위치한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserCouponService {

    private final UserCouponRepository userCouponRepository;

    public IssuedCouponsResponse showAllIssuedCoupons(Long userId) {
        UserCoupons userCoupons = UserCoupons.of(userCouponRepository.findByUserId(userId));
        List<Coupon> coupons = userCoupons.getCoupons();
        List<IssuedCoupon> list = coupons.stream()
                .map(IssuedCoupon::of)
                .toList();

        return IssuedCouponsResponse.builder()
                .issuedCoupons(list)
                .build();
    }
}