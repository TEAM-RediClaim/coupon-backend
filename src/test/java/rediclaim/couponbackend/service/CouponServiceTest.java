package rediclaim.couponbackend.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;
import static rediclaim.couponbackend.domain.UserType.CREATOR;
import static rediclaim.couponbackend.domain.UserType.NORMAL;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

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

    @Test
    @DisplayName("유저는 이전에 발급한 적이 없고, 재고가 있는 쿠폰을 발급받을 수 있다.")
    @Transactional
    void issue_coupon_success() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 10, creator));

        //when
        couponService.issueCoupon(user.getId(), coupon.getId(), 0L);

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
        User creator = userRepository.save(createCreator("쿠폰생성자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 10, creator));

        couponService.issueCoupon(user.getId(), coupon.getId(), 0L);        // 유저가 이미 쿠폰 발급한 상황

        //when //then
        assertThatThrownBy(() -> couponService.issueCoupon(user.getId(), coupon.getId(), 0L))
                .isInstanceOf(CustomException.class)
                .hasMessage(USER_ALREADY_HAS_COUPON.getMessage());
    }

    @Test
    @DisplayName("유저는 재고가 없는 쿠폰을 발급받을 수 없다.")
    @Transactional
    void can_not_issue_out_of_stock_coupon() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));
        User user = userRepository.save(createUser("유저1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 0, creator));

        //when //then
        assertThatThrownBy(() -> couponService.issueCoupon(user.getId(), coupon.getId(), 0L))
                .isInstanceOf(CustomException.class)
                .hasMessage(COUPON_OUT_OF_STOCK.getMessage());
    }

    @Test
    @DisplayName("재고가 있는 모든 쿠폰의 [id, 재고] 정보를 보여준다.")
    void show_All_Valid_Coupons() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));
        Coupon coupon1 = couponRepository.save(createCoupon("쿠폰1", 5, creator));
        Coupon coupon2 = couponRepository.save(createCoupon("쿠폰2", 3, creator));
        Coupon coupon3 = couponRepository.save(createCoupon("쿠폰3", 0, creator));

        //when
        ValidCouponsResponse result = couponService.showAllValidCoupons();

        //then
        assertThat(result.getValidCoupons()).hasSize(2)
                .extracting("couponId", "remainingCount")
                .containsExactlyInAnyOrder(
                        tuple(coupon1.getId(), 5),
                        tuple(coupon2.getId(), 3)
                );
    }

    @Test
    @DisplayName("유효한 쿠폰 생성자는 쿠폰을 생성할 수 있다.")
    @Transactional
    void create_coupon_success() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));

        //when
        Long savedId = couponService.createCoupon(creator.getId(), 10, "쿠폰1");

        //then
        Coupon savedCoupon = couponRepository.findById(savedId).get();
        assertThat(savedCoupon.getId()).isNotNull();
        assertThat(savedCoupon.getRemainingCount()).isEqualTo(10);
        assertThat(savedCoupon.getName()).isEqualTo("쿠폰1");
        assertThat(savedCoupon.getCreator()).isEqualTo(creator);
    }

    @Test
    @DisplayName("잘못된 쿠폰 생성자 id를 사용하면 쿠폰을 생성할 수 없다.")
    @Transactional
    void can_not_create_coupon_with_invalid_creator_id() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));

        //when //then
        assertThatThrownBy(() -> couponService.createCoupon(creator.getId() + 1, 10, "쿠폰1"))
                .isInstanceOf(CustomException.class)
                .hasMessage(USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("일반 유저는 쿠폰을 생성할 수 없다.")
    @Transactional
    void can_not_create_coupon_with_invalid_admin_code() throws Exception {
        //given
        User normalUser = userRepository.save(createUser("유저1"));

        //when //then
        assertThatThrownBy(() -> couponService.createCoupon(normalUser.getId(), 10, "쿠폰1"))
                .isInstanceOf(CustomException.class)
                .hasMessage(USER_NOT_ALLOWED_TO_CREATE_COUPON.getMessage());
    }

    private User createUser(String name) {
        return User.builder()
                .name(name)
                .userType(NORMAL)
                .build();
    }

    private User createCreator(String name) {
        return User.builder()
                .name(name)
                .userType(CREATOR)
                .build();
    }

    private Coupon createCoupon(String name, int remainingCount, User creator) {
        return Coupon.builder()
                .name(name)
                .remainingCount(remainingCount)
                .creator(creator)
                .build();
    }

}
