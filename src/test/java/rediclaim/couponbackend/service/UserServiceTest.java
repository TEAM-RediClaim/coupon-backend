package rediclaim.couponbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.IssuedCoupons;
import rediclaim.couponbackend.controller.response.RegisterAdminResponse;
import rediclaim.couponbackend.domain.Admin;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupon;
import rediclaim.couponbackend.repository.AdminRepository;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional(readOnly = true)
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminRepository adminRepository;

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        adminRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("유저가 발급한 모든 쿠폰의 [id, 이름]을 보여준다.")
    void show_all_issued_coupons() throws Exception {
        //given
        Admin admin = adminRepository.save(createAdmin("관리자1"));

        User user1 = userRepository.save(createUser("유저1"));
        Coupon coupon1 = couponRepository.save(createCoupon("쿠폰1", 10, admin));
        Coupon coupon2 = couponRepository.save(createCoupon("쿠폰2", 10, admin));
        userCouponRepository.save(createUserCoupon(user1, coupon1));
        userCouponRepository.save(createUserCoupon(user1, coupon2));

        User user2 = userRepository.save(createUser("유저2"));
        Coupon coupon3 = couponRepository.save(createCoupon("쿠폰3", 10, admin));
        Coupon coupon4 = couponRepository.save(createCoupon("쿠폰4", 10, admin));
        userCouponRepository.save(createUserCoupon(user2, coupon3));
        userCouponRepository.save(createUserCoupon(user2, coupon4));

        //when
        IssuedCoupons issuedCoupons = userService.showAllIssuedCoupons(user1.getId());      // user1이 발급한 쿠폰 조회

        //then
        assertThat(issuedCoupons.getIssuedCouponInfos()).hasSize(2)
                .extracting("couponId", "couponName")
                .containsExactlyInAnyOrder(
                        tuple(coupon1.getId(), coupon1.getName()),
                        tuple(coupon2.getId(), coupon2.getName())
                );
    }

    @Test
    @DisplayName("유저를 등록하고, 등록된 유저의 id를 반환한다.")
    @Transactional
    void register_user() throws Exception {
        //given
        String name = "유저1";

        //when
        Long savedUserId = userService.registerUser(name);

        //then
        User user = userRepository.findById(savedUserId).orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo(savedUserId);
        assertThat(user.getName()).isEqualTo(name);
    }

    @Test
    @DisplayName("관리자를 등록하고, 등록된 관리자의 [id, 관리자 code]를 반환한다.")
    void register_admin() throws Exception {
        //given
        String name = "관리자1";

        //when
        RegisterAdminResponse response = userService.registerAdmin(name);

        //then
        Admin admin = adminRepository.findById(response.getAdminId()).orElse(null);
        assertThat(admin).isNotNull();
        assertThat(admin.getId()).isEqualTo(response.getAdminId());
        assertThat(admin.getName()).isEqualTo(name);
        assertThat(admin.getCode()).isEqualTo(response.getAdminCode());
    }

    private UserCoupon createUserCoupon(User user, Coupon coupon) {
        return UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build();
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