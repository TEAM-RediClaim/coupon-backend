package rediclaim.couponbackend.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
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
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

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

        //then
        // 최종 쿠폰 재고 상태 확인
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

//    @Test
//    @DisplayName("멀티 스레드 환경에서 쿠폰 발급 요청이 동시에 들어올 경우, 쿠폰은 선착순으로 발급되어야 한다.")
//    void should_issue_coupon_in_FIFO_policy() throws Exception {
//        //given
//        User creator = userRepository.save(createCreator("쿠폰생성자1"));
//        Coupon coupon = couponRepository.save(createCoupon("쿠폰1", 2, creator));
//        List<User> users = new ArrayList<>();       // 10명의 테스트 유저
//        for (int i = 1; i <= 10; i++) {
//            users.add(userRepository.save(createUser("유저" + i)));
//        }
//        User firstUser = users.get(0);
//
//        for (User user : users) {
//            System.out.println("user.getId() = " + user.getId());       // 2 ~ 11
//        }
//
//        Map<User, Semaphore> semaphoreMap = new ConcurrentHashMap<>();
//        CountDownLatch readyLatch = new CountDownLatch(users.size());
//        CountDownLatch doneLatch = new CountDownLatch(users.size());
//        CountDownLatch lockAcquired = new CountDownLatch(1);
//        Map<User, Integer> issueOrder = new ConcurrentHashMap<>();
//        AtomicInteger issueCount = new AtomicInteger();
//
//        //when
//        // user1 ~ user10 순서로 스레드 생성
//        for (User user : users) {
//            semaphoreMap.put(user, new Semaphore(0));
//            new Thread(() -> {
//                readyLatch.countDown();
//                try {
//                    semaphoreMap.get(user).acquire();
//
//                    if (user.equals(firstUser)) {
//                        TransactionTemplate tt = new TransactionTemplate(transactionManager);
//                        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
//                        tt.execute(status -> {
//                            couponRepository.findById(coupon.getId()).orElseThrow();
//                            lockAcquired.countDown();
//                            try {
//                                Thread.sleep(3001);         // user1은 3000ms 보다 긴 시간동안 락 점유
//                            } catch (InterruptedException e) {
//                            }
//                            return null;
//                        });
//                    }
//
//                    couponService.issueCoupon(user.getId(), coupon.getId());
//                    issueOrder.put(user, issueCount.getAndIncrement());
//                } catch (InterruptedException e) {
//                } finally {
//                    doneLatch.countDown();
//                }
//            }).start();
//        }
//
//        for (User user : users) {
//            semaphoreMap.get(user).release();       // user1 ~ user10 순서로 스레드 시작
//            Thread.sleep(50);
//        }
//
//        //then
//        assertTrue(doneLatch.await(20, TimeUnit.SECONDS));      // 모든 스레드 종료 대기
//
//        assertThat(issueOrder.size()).isEqualTo(2);
//        for (User user : issueOrder.keySet()) {
//            System.out.println("user.getId() = " + user.getId());
//            System.out.println("issueOrder.get(user) = " + issueOrder.get(user));
//        }
//    }

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
