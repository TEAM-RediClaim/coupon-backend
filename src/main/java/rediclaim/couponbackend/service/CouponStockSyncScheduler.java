package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.repository.CouponRepository;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponStockSyncScheduler {

    private static final String STOCK_KEY_PREFIX = "STOCK_";

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void syncCouponStock() {
        List<Coupon> coupons = couponRepository.findAll();
        for (Coupon coupon : coupons) {
            String stockKey = STOCK_KEY_PREFIX + coupon.getId();
            String value = redisTemplate.opsForValue().get(stockKey);

            // redis에 키가 없으면 continue
            if (value == null) continue;

            try {
                int redisCount = Integer.parseInt(value);
                int dbCount = coupon.getRemainingCount();

                if (redisCount != dbCount) {
                    coupon.setRemainingCount(redisCount);
                    couponRepository.save(coupon);
                    log.info("쿠폰 재고 동기화 : couponId={}, dbCount={} -> redisCount={}", coupon.getId(), dbCount, redisCount);
                }
            } catch (NumberFormatException e) {
                log.warn("redis 재고 값 파싱 실패 : stockKey={}, value={}", stockKey, value);
            }
        }
    }
}
