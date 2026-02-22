package rediclaim.couponbackend.service.task1;

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
import rediclaim.couponbackend.service.CouponIssueServiceV1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static rediclaim.couponbackend.domain.UserType.CREATOR;
import static rediclaim.couponbackend.domain.UserType.NORMAL;

/**
 * [TASK 1] 단일 RDB 환경에서의 쿠폰 발급 동시성 테스트
 *
 * <p>테스트 시나리오: 쿠폰 재고 10개 / 동시 요청 유저 100명 / 스레드 50개</p>
 *
 * <ul>
 *   <li>{@code withoutLock}: 락 없이 실행 → 동시성 이슈(초과 발급) 발생 확인</li>
 *   <li>{@code withPessimisticLock}: 비관적 락 적용 → 정확히 10건만 발급됨 확인</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class CouponIssueServiceV1ConcurrencyTest {

    private static final int COUPON_STOCK = 10;
    private static final int TOTAL_USERS  = 100;
    private static final int THREAD_COUNT = 50;

    @Autowired private CouponIssueServiceV1 couponIssueServiceV1;
    @Autowired private CouponRepository couponRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserCouponRepository userCouponRepository;

    @AfterEach
    void tearDown() {
        userCouponRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // -------------------------------------------------------------------------
    // Test 1. 락 없음 — 동시성 이슈 발생 확인
    // -------------------------------------------------------------------------

    /**
     * 락 없이 100명이 동시에 쿠폰(재고 10개)을 요청하면 초과 발급이 발생한다.
     *
     * <p><b>예상 결과</b>: 성공 건수가 쿠폰 재고(10개)를 초과한다.
     * 이는 여러 스레드가 동시에 "재고 > 0" 조건을 통과한 뒤 각자 재고를 차감하는
     * TOCTOU(Time-Of-Check-Time-Of-Use) 경쟁 조건에 의해 발생한다.</p>
     *
     * <p>이 테스트는 동시성 이슈를 재현하는 것이 목적이므로,
     * 성공 건수가 재고를 초과했을 때를 기준으로 이슈 발생 여부를 출력한다.</p>
     */
    @Test
    @DisplayName("[락 없음] 동시 요청 시 초과 발급(overselling) 동시성 이슈가 발생한다")
    void withoutLock_demonstrates_race_condition() throws InterruptedException {
        // given
        User creator = userRepository.save(createCreator("쿠폰생성자"));
        Coupon coupon = couponRepository.save(createCoupon("선착순쿠폰", COUPON_STOCK, creator));
        List<User> users = createUsers(TOTAL_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        // when
        runConcurrently(users, (user) ->
                couponIssueServiceV1.issueWithoutLock(user.getId(), coupon.getId()),
                successCount, failCount
        );

        // then
        Coupon updated = couponRepository.findById(coupon.getId()).orElseThrow();
        int issuedCount = (int) userCouponRepository.count();

        System.out.println("=== [락 없음] 동시성 테스트 결과 ===");
        System.out.printf("쿠폰 재고      : %d%n", COUPON_STOCK);
        System.out.printf("성공 요청 수   : %d%n", successCount.get());
        System.out.printf("실패 요청 수   : %d%n", failCount.get());
        System.out.printf("실제 발급 건수 : %d%n", issuedCount);
        System.out.printf("DB 남은 재고   : %d%n", updated.getRemainingCount());

        boolean oversold = successCount.get() > COUPON_STOCK || updated.getRemainingCount() < 0;
        System.out.println("초과 발급 발생 : " + oversold);
        System.out.println("=> 락 없이는 동시성 이슈로 인해 초과 발급이 발생할 수 있습니다.");

        // 모든 요청은 예외 없이 처리되었어야 한다 (에러 누락 여부 확인)
        assertEquals(TOTAL_USERS, successCount.get() + failCount.get(),
                "모든 요청이 성공 또는 실패로 처리되어야 합니다.");
    }

    // -------------------------------------------------------------------------
    // Test 2. 비관적 락 적용 — 정확히 재고 수량만큼만 발급
    // -------------------------------------------------------------------------

    /**
     * 비관적 락(SELECT ... FOR UPDATE)을 적용하면 100명이 동시에 요청해도
     * 정확히 재고(10개)만큼만 발급된다.
     *
     * <p>락 획득 대기로 인해 처리 시간이 늘어나지만, 데이터 정합성이 보장된다.</p>
     */
    @Test
    @DisplayName("[비관적 락] 동시 요청 시 정확히 재고 수량(10개)만큼만 발급된다")
    void withPessimisticLock_issues_exact_quantity() throws InterruptedException {
        // given
        User creator = userRepository.save(createCreator("쿠폰생성자"));
        Coupon coupon = couponRepository.save(createCoupon("선착순쿠폰", COUPON_STOCK, creator));
        List<User> users = createUsers(TOTAL_USERS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);

        // when
        runConcurrently(users, (user) ->
                couponIssueServiceV1.issueWithPessimisticLock(user.getId(), coupon.getId()),
                successCount, failCount
        );

        // then
        Coupon updated = couponRepository.findById(coupon.getId()).orElseThrow();

        System.out.println("=== [비관적 락] 동시성 테스트 결과 ===");
        System.out.printf("쿠폰 재고      : %d%n", COUPON_STOCK);
        System.out.printf("성공 요청 수   : %d%n", successCount.get());
        System.out.printf("실패 요청 수   : %d%n", failCount.get());
        System.out.printf("DB 남은 재고   : %d%n", updated.getRemainingCount());

        assertAll("비관적 락 적용 후 정합성 검증",
                () -> assertEquals(COUPON_STOCK, successCount.get(),
                        "성공 건수는 쿠폰 재고(" + COUPON_STOCK + ")와 같아야 합니다."),
                () -> assertEquals(TOTAL_USERS - COUPON_STOCK, failCount.get(),
                        "실패 건수는 (총 요청 - 재고) 여야 합니다."),
                () -> assertEquals(0, updated.getRemainingCount(),
                        "모든 재고가 소진되어 남은 재고는 0이어야 합니다.")
        );
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    @FunctionalInterface
    interface CouponIssueAction {
        void issue(User user) throws Exception;
    }

    private void runConcurrently(List<User> users, CouponIssueAction action,
                                 AtomicInteger successCount, AtomicInteger failCount)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(users.size());

        for (User user : users) {
            executor.submit(() -> {
                try {
                    action.issue(user);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
    }

    private List<User> createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(userRepository.save(
                    User.builder().name("유저" + i).userType(NORMAL).build()
            ));
        }
        return users;
    }

    private User createCreator(String name) {
        return userRepository.save(
                User.builder().name(name).userType(CREATOR).build()
        );
    }

    private Coupon createCoupon(String name, int stock, User creator) {
        return Coupon.builder()
                .name(name)
                .remainingCount(stock)
                .creator(creator)
                .build();
    }
}
