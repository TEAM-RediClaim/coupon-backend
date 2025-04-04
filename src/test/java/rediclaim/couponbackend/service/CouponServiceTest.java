package rediclaim.couponbackend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.exception.BadRequestException;
import rediclaim.couponbackend.repository.AdminRepository;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.COUPON_OUT_OF_STOCK;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.USER_ALREADY_HAS_COUPON;

@SpringBootTest
@ActiveProfiles("test")
@Transactional(readOnly = true)
class CouponServiceTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Test
    @DisplayName("유저는 이전에 발급한 적이 없고, 재고가 있는 쿠폰을 발급받을 수 있다.")
    @Transactional
    void issue_coupon_success() throws Exception {
        //given
        Admin admin = adminRepository.save(createAdmin("관리자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 10, admin));

        //when
        couponService.issueCoupon(user.getId(), coupon.getId());

        //then
        assertThat(coupon.getRemainingCount()).isEqualTo(9);

        UserCoupons userCoupons = UserCoupons.of(userCouponRepository.findByUserId(user.getId()));
        assertThat(userCoupons.hasCoupon(coupon.getId())).isTrue();
    }

    @Test
    @DisplayName("유저는 이미 발급받은 쿠폰을 재발급 받을 수 없다.")
    @Transactional
    void can_not_issue_coupon_for_already_issued() throws Exception {
        //given
        Admin admin = adminRepository.save(createAdmin("관리자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 10, admin));

        couponService.issueCoupon(user.getId(), coupon.getId());        // 유저가 이미 쿠폰 발급한 상황

        //when //then
        assertThatThrownBy(() -> couponService.issueCoupon(user.getId(), coupon.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(USER_ALREADY_HAS_COUPON.getMessage());
    }

    @Test
    @DisplayName("유저는 재고가 없는 쿠폰을 발급받을 수 없다.")
    @Transactional
    void can_not_issue_out_of_stock_coupon() throws Exception {
        //given
        Admin admin = adminRepository.save(createAdmin("관리자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 0, admin));

        //when //then
        assertThatThrownBy(() -> couponService.issueCoupon(user.getId(), coupon.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(COUPON_OUT_OF_STOCK.getMessage());
    }



    private User createUser(String name) {
        return User.builder()
                .name(name)
                .build();
    }

    private Admin createAdmin(String name) {
        return Admin.builder()
                .name(name)
                .build();
    }

    private Coupon createCoupon(String name, int remainingCount, Admin couponCreator) {
        return Coupon.builder()
                .name(name)
                .remainingCount(remainingCount)
                .couponCreator(couponCreator)
                .build();
    }

}