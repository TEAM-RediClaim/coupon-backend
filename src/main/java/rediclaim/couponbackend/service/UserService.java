package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.IssuedCoupon;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.controller.response.RegisterAdminResponse;
import rediclaim.couponbackend.domain.Admin;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupons;
import rediclaim.couponbackend.repository.AdminRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;

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
                .build()).getId();
    }

    @Transactional
    public RegisterAdminResponse registerAdmin(String name) {
        Admin savedAdmin = adminRepository.save(Admin.createNew(name));
        return RegisterAdminResponse.of(savedAdmin);
    }
}
