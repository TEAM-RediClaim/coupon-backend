package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.controller.response.ValidCoupon;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.Collections;
import java.util.List;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private static final String STOCK_KEY_PREFIX = "STOCK_";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;

    // Lua script for atomic DECR/INCR
    private static final String ATOMIC_DECR_SCRIPT =
            "local v = redis.call('DECR', KEYS[1])\n" +
                    "if v < 0 then\n" +
                    "  redis.call('INCR', KEYS[1])\n" +
                    "  return -1\n" +
                    "end\n" +
                    "return v";

    /**
     * 유저가 발급한 적이 없는 쿠폰이고, 재고가 있을 경우 해당 유저에게 쿠폰을 발급해준다
     * transactional 지우기
     */
    public void issueCoupon(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        String stockKey = STOCK_KEY_PREFIX + couponId;
        Long remaining = allocateStockAtomically(stockKey);     // 쿠폰 재고 감소
        if (remaining < 0) {        // 해당 쿠폰 재고 없음
            throw new CustomException(COUPON_OUT_OF_STOCK);
        }

        try {
            // UserCoupon save
            userCouponRepository.save(UserCoupon.builder()
                    .user(user)
                    .coupon(coupon)
                    .build());

            // Coupon update
            syncCouponCountFromRedis(coupon, stockKey);
        } catch (DataIntegrityViolationException e) {       // 유저가 이미 특정 쿠폰을 발급받은 경우 -> 쿠폰 재고 복원
            redisTemplate.opsForValue().increment(stockKey);
            syncCouponCountFromRedis(coupon, stockKey);
            throw new CustomException(USER_ALREADY_HAS_COUPON);
        } catch (Exception e) {
            // DB 에러 발생시 redis 재고 복원
            redisTemplate.opsForValue().increment(stockKey);
            syncCouponCountFromRedis(coupon, stockKey);
            log.error(e.getMessage());
            throw new CustomException(DATABASE_ERROR);
        }
    }

    /**
     * Redis에서 Lua 스크립트로 DECR/INCR을 원자화하여 재고를 할당
     * @return 남은 재고(>=0) 또는 -1(재고 부족)
     */
    private Long allocateStockAtomically(String key) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(ATOMIC_DECR_SCRIPT);
        script.setResultType(Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key));
        if (result == null) {
            throw new RuntimeException("Failed to execute stock script for key: " + key);
        }
        return result;
    }

    private void syncCouponCountFromRedis(Coupon coupon, String key) {
        // redis에 있는 쿠폰 재고 정보가 최신 정보이다 -> Coupon의 재고 정보는 redis의 값으로 update 해주면 된다
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try {
                int redisCount = Integer.parseInt(val);
                coupon.setRemainingCount(redisCount);
                couponRepository.save(coupon);
            } catch (NumberFormatException e) {
                log.error("unable to parse redis stock value : {}", val);
                throw e;
            }
        }
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

        return saved.getId();
    }
}
