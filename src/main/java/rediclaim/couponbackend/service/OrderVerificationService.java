package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import rediclaim.couponbackend.controller.response.LogRecord;

import java.util.List;

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

    /**
     * 전체 쿠폰 발급 완료 로그를 반환
     * -> redis list에 push된 순서대로 반환한 결과가 requestSequence 값(= 쿠폰 발급 요청 순서)에 대하여 1, 2, 3, ,, 의 결과를 가져야 선착순 쿠폰 발급이 성공임을 보장할 수 있다
     */
    public List<LogRecord> getCompletionLogs(Long couponId) {
        String completionKey = COMPLETION_ORDER_PREFIX + couponId;
        List<String> rawCompletions = redisTemplate.opsForList().range(completionKey, 0, -1);

        return rawCompletions.stream()
                .map(s -> {
                    String[] parts = s.split(":", 3);
                    Long userId = Long.parseLong(parts[0]);
                    String timestamp = parts[1];
                    Long requestSequence = Long.parseLong(parts[2]);        // 쿠폰 발급 요청 순번

                    return LogRecord.builder()
                            .requestSequence(requestSequence)
                            .userId(userId)
                            .timestamp(timestamp)
                            .build();
                })
                .toList();
    }
}
