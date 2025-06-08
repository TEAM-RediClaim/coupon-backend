package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.controller.response.ValidCoupon;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.domain.event.CouponIssueEvent;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final String STOCK_KEY_PREFIX = "STOCK_";
    private static final String ISSUED_USERS_KEY_PREFIX = "ISSUED_USERS_";

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Lua script for user duplicate check & atomic DECR
    private static final String ATOMIC_DECR_SCRIPT =
            "local userId = ARGV[1]\n" +
                    "local stock = redis.call('GET', KEYS[1])\n" +
                    "if redis.call('SISMEMBER', KEYS[2], userId) == 1 then\n" +
                    "  return -2\n" +                // 이미 발급된 사용자
                    "end\n" +
                    "if tonumber(stock) <= 0 then\n" +
                    "  return -1\n" +                // 재고 부족
                    "end\n" +
                    "redis.call('DECR', KEYS[1])\n" +
                    "redis.call('SADD', KEYS[2], userId)\n" +  // 발급된 user set에 추가
                    "return redis.call('GET', KEYS[1])";

    /**
     * 1. 사용자 쿠폰 발급 여부 검사 및 redis 쿠폰 재고 차감
     * 2. DB I/O 을 위해 event publish (성공/실패 콜백 포함)
     */
    public void issueCoupon(Long userId, Long couponId) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        String issuedUsersKey = ISSUED_USERS_KEY_PREFIX + couponId;

        Long remaining = allocateStockAtomically(stockKey, issuedUsersKey, userId);
        if (remaining == -2) {
            throw new CustomException(USER_ALREADY_HAS_COUPON);
        }
        if (remaining == -1) {
            throw new CustomException(COUPON_OUT_OF_STOCK);
        }

        sendIssueEvent(userId, couponId, stockKey, issuedUsersKey);
    }

    /**
     * @return
     * -2 : 이미 해당 쿠폰을 발급한 유저
     * -1 : 쿠폰 재고 부족
     * 0 이상 : 쿠폰 발급 성공 -> 쿠폰 재고값 반환
     */
    private Long allocateStockAtomically(String stockKey, String issuedUsersKey, Long userId) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(ATOMIC_DECR_SCRIPT);
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script, List.of(stockKey, issuedUsersKey), userId.toString());
        if (result == null) {
            throw new RuntimeException("Failed to execute stock script for key: " + stockKey);
        }
        return result;
    }

    private void sendIssueEvent(Long userId, Long couponId, String stockKey, String issuedUsersKey) {
        // 이벤트 및 메시지 생성
        CouponIssueEvent event = CouponIssueEvent.builder()
                .userId(userId)
                .couponId(couponId)
                .build();
        Message<CouponIssueEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, "coupons")
                .setHeader(KafkaHeaders.KEY,   couponId.toString())
                .build();

        // 비동기 전송 (90초 타임아웃 + 성공/실패 콜백)
        kafkaTemplate.send(message)
                .orTimeout(90, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 메시지 전송 실패: userId={}, couponId={}", userId, couponId, ex);
                        // Redis 보상 처리
                        redisTemplate.opsForValue().increment(stockKey);
                        redisTemplate.opsForSet().remove(issuedUsersKey, userId.toString());

                        // TODO: 사용자에게 사과 메시지 전송

                    } else {
                        log.info("Kafka 메시지 전송 성공: userId={}, couponId={}", userId, couponId);
                    }
                });
    }

    public ValidCouponsResponse showAllValidCoupons() {
        List<Coupon> coupons = couponRepository.findByRemainingCountGreaterThan(0);
        List<ValidCoupon> list = coupons.stream()
                .map(ValidCoupon::of)
                .toList();
        return ValidCouponsResponse.builder()
                .validCoupons(list)
                .build();
    }

    @Transactional
    public Long createCoupon(Long creatorId, int quantity, String couponName) {
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        if (!creator.isCreator()) {
            throw new CustomException(USER_NOT_ALLOWED_TO_CREATE_COUPON);
        }

        Coupon saved = couponRepository.save(Coupon.builder()
                .name(couponName)
                .remainingCount(quantity)
                .creator(creator)
                .build());

        String stockKey = STOCK_KEY_PREFIX + saved.getId();
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));

        String issuedUsersKey = ISSUED_USERS_KEY_PREFIX + saved.getId();
        redisTemplate.delete(issuedUsersKey);       // 이전에 해당 쿠폰을 발급받은 유저들 정보는 전부 삭제

        return saved.getId();
    }
}
