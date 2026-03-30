package rediclaim.issuer.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * gate-app 이 관리하는 Active Queue 를 읽기 전용으로 조회한다.
 *
 * <p>Active Queue 키 형식: {@code gate:active:{eventId}:{userId}}
 * gate-app 이 SET EX 로 생성하며, TTL 만료 시 Redis 가 자동 제거한다.
 * issuer-api-app 은 이 키의 존재 여부만 확인하고 절대 write 하지 않는다.</p>
 */
@Repository
@RequiredArgsConstructor
public class ActiveQueueRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String ACTIVE_KEY_PREFIX = "gate:active:";

    /**
     * 해당 유저가 Active Queue 에 있는지 확인 (read-only)
     */
    public boolean isActive(Long eventId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_KEY_PREFIX + eventId + ":" + userId));
    }
}