package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.ValidCoupons;
import rediclaim.couponbackend.controller.response.ValidCouponInfo;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.exception.BadRequestException;
import rediclaim.couponbackend.repository.AdminRepository;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

    /**
     * 유저가 발급한 적이 없는 쿠폰이고, 재고가 있을 경우 해당 유저에게 쿠폰을 발급해준다
     */
    @Transactional
    public void issueCoupon(Long userId, Long couponId) {
        UserCoupons userCoupons = UserCoupons.of(userCouponRepository.findByUserId(userId));
        if (userCoupons.hasCoupon(couponId)) {
            throw new BadRequestException(USER_ALREADY_HAS_COUPON);
        }

        Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new BadRequestException(COUPON_NOT_FOUND));
        if (!coupon.hasRemainingStock()) {
            throw new BadRequestException(COUPON_OUT_OF_STOCK);
        }

        coupon.decrementRemainingCount();
        User user = userRepository.findById(userId).orElseThrow(() -> new BadRequestException(USER_NOT_FOUND));
        userCouponRepository.save(UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build());
    }

    public ValidCoupons showAllValidCoupons() {
        List<Coupon> coupons = couponRepository.findByRemainingCountGreaterThan(0);
        List<ValidCouponInfo> list = coupons.stream()
                .map(ValidCouponInfo::of)
                .toList();
        return ValidCoupons.builder()
                .validCouponInfos(list)
                .build();
    }

    @Transactional
    public Long createCoupon(Long adminId, Long adminCode, int quantity, String couponName) {
        Admin admin = adminRepository.findById(adminId).orElseThrow(() -> new BadRequestException(ADMIN_NOT_FOUND));
        if (!admin.isValidAdminCode(adminCode)) {
            throw new BadRequestException(INVALID_ADMIN_CODE);
        }

        return couponRepository.save(Coupon.builder()
                .name(couponName)
                .remainingCount(quantity)
                .couponCreator(admin)
                .build()).getId();
    }
}
