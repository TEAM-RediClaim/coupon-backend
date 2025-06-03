package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupon;
import rediclaim.couponbackend.domain.event.CouponIssueEvent;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.COUPON_NOT_FOUND;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueConsumer {

    private static final String STOCK_KEY_PREFIX = "STOCK_";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;

    @KafkaListener(topics = "coupon-issue-events")
    @Transactional
    public void handleCouponIssue(CouponIssueEvent event) {
        Long userId = event.getUserId();
        Long couponId = event.getCouponId();
        log.info("CouponIssueEvent 수신 : userId={}, couponId={}", userId, couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        String stockKey = STOCK_KEY_PREFIX + couponId;

        try {
            // UserCoupon save
            userCouponRepository.save(
                    UserCoupon.builder()
                            .user(user)
                            .coupon(coupon)
                            .build()
            );

            // Coupon 재고 update(redis의 쿠폰 재고 정보로 update)
            String val = redisTemplate.opsForValue().get(stockKey);
            if (val != null) {
                int redisCount = Integer.parseInt(val);
                coupon.setRemainingCount(redisCount);
                couponRepository.save(coupon);
            }

            log.info("CouponIssueEvent 처리 완료 : userId={}, couponId={}", userId, couponId);
        } catch (DataIntegrityViolationException e) {
            log.warn("중복 CouponIssueEvent 수신 -> 무시합니다. : userId={}, couponId={}", userId, couponId);
        } catch (Exception e) {
            // UserCoupon 중복 save 를 제외한 다른 에러
            // -> 재시도
            log.error("CouponIsseuEvent 처리 중 예외 발생 : {}, message={}", e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }
}
