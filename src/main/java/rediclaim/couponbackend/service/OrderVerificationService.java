package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderVerificationService {

    private static final String REQUEST_SEQ_PREFIX = "REQ_SEQ_";
    private static final String REQUEST_ORDER_PREFIX = "REQ_ORDER_";
    private static final String COMPLETION_ORDER_PREFIX = "COMP_ORDER_";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 요청이 들어온 순서대로 번호 할당
     */
    public Long assignRequestOrder(Long userId, Long couponId) {
        String seqKey = REQUEST_SEQ_PREFIX + couponId;
        String orderKey = REQUEST_ORDER_PREFIX + couponId;

        // 해당 쿠폰의 발급 요청 순서
        Long requestSequence = redisTemplate.opsForValue().increment(seqKey);

        // 요청 순서 기록: {순서번호: "userId:timestamp"} -> 해시 타입으로 저장
        String record = userId + ":" + System.currentTimeMillis();
        redisTemplate.opsForHash().put(orderKey, requestSequence.toString(), record);

        return requestSequence;
    }

    /**
     * 쿠폰 발급 완료 순서 기록
     */
    public void recordCompletion(Long userId, Long couponId, Long requestSequence) {
        String completionKey = COMPLETION_ORDER_PREFIX + couponId;

        // 완료 순서 기록: 완료된 시점의 타임스탬프와 함께 -> list 타입으로 저장
        String record = userId + ":" + System.currentTimeMillis() + ":" + requestSequence;
        redisTemplate.opsForList().rightPush(completionKey, record);
    }
}
