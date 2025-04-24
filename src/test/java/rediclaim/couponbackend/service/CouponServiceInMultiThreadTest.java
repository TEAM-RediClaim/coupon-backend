package rediclaim.couponbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static rediclaim.couponbackend.domain.UserType.*;

@SpringBootTest
@ActiveProfiles("test")
class CouponServiceInMultiThreadTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("멀티 스레드 환경에서 쿠폰 발급 요청이 동시에 들어올 경우, 쿠폰은 정확히 정해진 수량만큼 발급되어야 한다.")
    void should_issue_exact_quantity_in_multi_thread() throws Exception {
        //given
        User creator = userRepository.save(createCreator("쿠폰생성자1"));
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 100, creator));
        List<User> users = new ArrayList<>();       // 1000명의 테스트 유저
        for (int i = 1; i <= 1000; i++) {
            users.add(userRepository.save(createUser("유저" + i)));
        }

        int totalUsers = users.size();
        ExecutorService executor = Executors.newFixedThreadPool(50);        // 50개의 스레드 풀 생성 -> 멀티 스레드 환경
        CountDownLatch latch = new CountDownLatch(totalUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        //when
        // 1000명의 유저가 동시에 쿠폰 발급 요청을 수행하도록 task submit
        for (User user : users) {
            executor.submit(() -> {
                try {
                    couponService.issueCoupon(user.getId(), coupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();      // 각 task가 완료되면 latch count 1 감소
                }
            });
        }

        // 모든 task가 완료될 때까지 메인 스레드는 대기
        latch.await();
        executor.shutdown();

        //then
        // 최종 쿠폰 재고 상태 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();

        System.out.println("성공한 요청 건수: " + successCount.get());
        System.out.println("실패한 요청 건수: " + failCount.get());
        System.out.println("남은 쿠폰 재고: " + updatedCoupon.getRemainingCount());

        assertAll("쿠폰 발급 통계",
                () -> assertEquals(100, successCount.get(), "성공 요청 수는 100이어야 합니다."),
                () -> assertEquals(900, failCount.get(), "실패 요청수는 900이어야 합니다."),
                () -> assertEquals(0, updatedCoupon.getRemainingCount(), "남은 쿠폰 재고는 0이어야 합니다.")
        );
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