package rediclaim.couponbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static rediclaim.couponbackend.domain.UserType.*;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka
@TestPropertySource(properties = {
        // spring.embedded.kafka.brokers 에서 자동으로 생성된 브로커 주소를 사용하도록 오버라이드
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class CouponServiceInMultiThreadTest {

    @Autowired
    private CouponService couponService;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CouponStockSyncScheduler couponStockSyncScheduler;

    private static final String STOCK_KEY_PREFIX = "STOCK_";

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
        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 10, creator));

        String stockKey = STOCK_KEY_PREFIX + coupon.getId();
        redisTemplate.opsForValue().set(stockKey, String.valueOf(10));      // redis 세팅

        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            users.add(userRepository.save(createUser("유저" + i)));
        }

        int totalUsers = users.size();
        ExecutorService executor = Executors.newFixedThreadPool(50);        // 50개의 스레드 풀 생성 -> 멀티 스레드 환경
        CountDownLatch latch = new CountDownLatch(totalUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        //when
        // 유저들이 동시에 쿠폰 발급 요청을 수행하도록 task submit
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

        couponStockSyncScheduler.syncCouponStock();     // redis 재고와 DB 동기화를 수동으로 실행

        //then
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();

        System.out.println("성공한 요청 건수: " + successCount.get());
        System.out.println("실패한 요청 건수: " + failCount.get());
        System.out.println("남은 쿠폰 재고: " + updatedCoupon.getRemainingCount());
        assertAll("쿠폰 발급 통계",
                () -> assertEquals(10, successCount.get(), "성공 요청 수는 100이어야 합니다."),
                () -> assertEquals(90, failCount.get(), "실패 요청수는 900이어야 합니다."),
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
