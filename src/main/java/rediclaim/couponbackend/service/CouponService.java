package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
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

import static rediclaim.couponbackend.domain.CouponIssueFailReason.DUPLICATE;
import static rediclaim.couponbackend.domain.CouponIssueFailReason.OUT_OF_STOCK;
import static rediclaim.couponbackend.domain.CouponIssueStatus.FAIL;
import static rediclaim.couponbackend.domain.CouponIssueStatus.SUCCESS;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final String SEQ_KEY_PREFIX = "REQ_SEQ_";
    private static final String STOCK_KEY_PREFIX = "STOCK_";
    private static final String ISSUED_USERS_KEY_PREFIX = "ISSUED_USERS_";
    private static final String LOG_KEY_PREFIX = "REQ_LOG_";

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Lua script: atomic seq assign, duplicate check, stock check & decr
    private static final String ATOMIC_SCRIPT = """
        local seqKey    = KEYS[1]
        local stockKey  = KEYS[2]
        local issuedKey = KEYS[3]
        local userId    = ARGV[1]

        -- 1) assign sequence
        local seq = redis.call('INCR', seqKey)

        -- 2) duplicate check
        if redis.call('SISMEMBER', issuedKey, userId) == 1 then
            return {-2, seq}
        end

        -- 3) stock check
        local stock = tonumber(redis.call('GET', stockKey))
        if not stock or stock <= 0 then
            return {-1, seq}
        end

        -- 4) decrement stock & record issued user
        redis.call('DECR', stockKey)
        redis.call('SADD', issuedKey, userId)

        -- return {remainingStock, sequence}
        return {stock - 1, seq}
    """;

    /**
     * 1. 사용자 쿠폰 발급 여부 검사 및 redis 쿠폰 재고 차감
     * 2. DB I/O 을 위해 event publish (성공/실패 콜백 포함)
     */
    public void issueCoupon(Long userId, Long couponId) {
        String seqKey = SEQ_KEY_PREFIX + couponId;
        String stockKey = STOCK_KEY_PREFIX + couponId;
        String issuedUsersKey = ISSUED_USERS_KEY_PREFIX + couponId;
        String logKey = LOG_KEY_PREFIX + couponId;

        Long remaining = allocateStockAtomically(seqKey, stockKey, issuedUsersKey, logKey, userId);
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
    private Long allocateStockAtomically(String seqKey, String stockKey, String issuedUsersKey, String logKey, Long userId) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(ATOMIC_SCRIPT);
        script.setResultType(List.class);

        List<Long> result = redisTemplate.execute(script, List.of(seqKey, stockKey, issuedUsersKey), userId.toString());
        if (result == null || result.size() != 2) {
            throw new RuntimeException("Lua script execution failed");
        }

        recordIssueLog(logKey, result.get(0), result.get(1), userId);       // log 기록

        return result.get(0);
    }

    private void recordIssueLog(String logKey, Long remaining, Long requestSeq, Long userId) {
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] logKeyBytes = logKey.getBytes();
            byte[] fieldBytes = String.valueOf(requestSeq).getBytes();
            byte[] valueBytes;

            if (remaining == -2) {
                valueBytes = String.format("%d:%d:%s:%s", requestSeq, userId, FAIL.getValue(), DUPLICATE.getValue()).getBytes();
            } else if (remaining == -1) {
                valueBytes = String.format("%d:%d:%s:%s", requestSeq, userId, FAIL.getValue(), OUT_OF_STOCK.getValue()).getBytes();
            } else {
                valueBytes = String.format("%d:%d:%s:null", requestSeq, userId, SUCCESS.getValue()).getBytes();
            }

            connection.execute("HSET", logKeyBytes, fieldBytes, valueBytes);
            return null;
        });
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

        // redis에 재고 초기화
        String stockKey = STOCK_KEY_PREFIX + saved.getId();
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));

        // 기존 쿠폰 발급자 set, 요청 순번, 로그 초기화
        String issuedUsersKey = ISSUED_USERS_KEY_PREFIX + saved.getId();
        String seqKey = SEQ_KEY_PREFIX + saved.getId();
        String logKey = LOG_KEY_PREFIX + saved.getId();
        redisTemplate.delete(issuedUsersKey);
        redisTemplate.delete(seqKey);
        redisTemplate.delete(logKey);

        return saved.getId();
    }
}
