package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.IssuedCoupon;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupons;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;

import static rediclaim.couponbackend.domain.UserType.CREATOR;
import static rediclaim.couponbackend.domain.UserType.NORMAL;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

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

    @Transactional
    public Long registerUser(String name) {
        return userRepository.save(User.builder()
                .name(name)
                .userType(NORMAL)
                .build()).getId();
    }

    @Transactional
    public Long registerCreator(String name) {
        return userRepository.save(User.builder()
                .name(name)
                .userType(CREATOR)
                .build()).getId();
    }
}
