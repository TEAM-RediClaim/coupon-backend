package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static rediclaim.couponbackend.domain.CouponIssueFailReason.OUT_OF_STOCK;
import static rediclaim.couponbackend.domain.CouponIssueStatus.FAIL;
import static rediclaim.couponbackend.domain.CouponIssueStatus.SUCCESS;

@Service
@RequiredArgsConstructor
public class CouponIssueLogService {

    private static final String REQUEST_LOG_PREFIX = "REQ_LOG_";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 선착순 쿠폰 발급 검증
     * [요청 순번이 작고, 재고부족으로 인한 실패] 인 로그가 있고,
     * [요청 순번이 크고, 쿠폰 발급 성공] 인 로그가 있으면 false, 그렇지 않으면 true
     */
    public boolean verifyFirstComeFirstServe(Long couponId) {
        String logKey = REQUEST_LOG_PREFIX + couponId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(logKey);

        List<String[]> records = entries.values().stream()
                .map(v -> ((String) v).split(":", 4))       // [requestSeq, userId, status, reason]
                .sorted(Comparator.comparingLong(parts -> Long.parseLong(parts[0])))
                .toList();

        long maxFailedSeq = -1;
        for (String[] parts : records) {
            long requestSeq = Long.parseLong(parts[0]);
            String status = parts[2];
            String reason = parts[3];

            if (FAIL.getValue().equals(status) && OUT_OF_STOCK.getValue().equals(reason)) {
                maxFailedSeq = requestSeq;
            }

            if (SUCCESS.getValue().equals(status) && maxFailedSeq >= 0 && requestSeq > maxFailedSeq) {
                return false;
            }
        }

        return true;
    }
}
