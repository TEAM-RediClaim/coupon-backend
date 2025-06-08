package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.repository.CouponRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponStockSyncScheduler {

    private static final String STOCK_KEY_PREFIX = "STOCK_";

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;

    /**
     * 5분마다 전체 쿠폰 재고 동기화 처리
     **/
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void syncCouponStock() {
        couponRepository.findAll().forEach(this::syncStockForCoupon);
    }

    /**
     * 개별 쿠폰에 대해 Redis → DB 동기화
     **/
    private void syncStockForCoupon(Coupon coupon) {
        String stockKey = STOCK_KEY_PREFIX + coupon.getId();

        try {
            String value = redisTemplate.opsForValue().get(stockKey);
            if (!StringUtils.hasText(value)) {
                log.debug("Redis에 stockKey가 없음: {}", stockKey);
                return;
            }

            int redisCount = Integer.parseInt(value);
            updateCouponStock(coupon, redisCount);
        } catch (Exception e) {
            log.error("syncCouponStock 예외 발생: couponId={}, stockKey={}, errorMessage={}", coupon.getId(), stockKey, e.getMessage(), e);
        }
    }

    /**
     * DB상의 Coupon 재고와 다를 경우에만 업데이트
     **/
    private void updateCouponStock(Coupon coupon, int redisCount) {
        int dbCount = coupon.getRemainingCount();
        if (redisCount != dbCount) {
            coupon.setRemainingCount(redisCount);
            couponRepository.save(coupon);
            log.info("쿠폰 재고 동기화 완료: couponId={}, dbCount={} -> redisCount={}", coupon.getId(), dbCount, redisCount);
        }
    }
}
