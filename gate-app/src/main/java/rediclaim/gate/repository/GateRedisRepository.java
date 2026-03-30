package rediclaim.gate.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class GateRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String QUEUE_KEY_PREFIX = "gate:queue:";
    private static final String QUEUE_RANK_KEY_PREFIX = "gate:queue:rank:";

    private static final String PROCESSING_KEY_PREFIX = "gate:processing:";
    private static final String PROCESSING_RANK_KEY_PREFIX = "gate:processing:rank:";

    private static final String ACTIVE_KEY_PREFIX = "gate:active:";

    private String queueKey(Long eventId) {
        return QUEUE_KEY_PREFIX + eventId;
    }
    private String queueRankKey(Long eventId) {
        return QUEUE_RANK_KEY_PREFIX + eventId;
    }
    private String processingKey(Long eventId) {
        return PROCESSING_KEY_PREFIX + eventId;
    }
    private String processingRankKey(Long eventId) {
        return PROCESSING_RANK_KEY_PREFIX + eventId;
    }

    /**
     * 대기열에 유저 추가
     */
    public GateEnqueueDto enqueueLua(Long eventId, Long userId) {
        String qKey = queueKey(eventId);
        String rKey = queueRankKey(eventId);
        String val = userId.toString();

        String lua = """
            local qKey = KEYS[1]
            local rKey = KEYS[2]
            local val = ARGV[1]
            
            -- 1. 이미 대기열에 있는지 확인 (중복 방지)
            if redis.call('ZSCORE', qKey, val) then
                return {0, redis.call('ZRANK', qKey, val)}
            end
            
            -- 2. 번호표 발급 (Atomic Increment)
            local score = redis.call('INCR', rKey)
            
            -- 3. 대기열 추가
            redis.call('ZADD', qKey, score, val)
            
            -- 4. 현재 순번 조회
            local rank = redis.call('ZRANK', qKey, val)
            
            return {1, rank}
        """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);

        // 결과: [isNew(1/0), rank(Long)]
        List<Long> result = redisTemplate.execute(script, List.of(qKey, rKey), val);

        if (result.isEmpty()) return new GateEnqueueDto(false, -1L);

        return new GateEnqueueDto(result.get(0) == 1, result.get(1));
    }

    /**
     * 현재 대기 순번 조회 (0-based)
     */
    public Optional<Long> getRank(Long eventId, Long userId) {
        Long rank = redisTemplate.opsForZSet().rank(queueKey(eventId), userId.toString());
        return Optional.ofNullable(rank);
    }

    /**
     * processing에 있는지 여부 조회
     */
    public boolean isProcessing(Long eventId, Long userId) {
        // ZSCORE가 존재하면 Processing 상태임 (Score는 timestamp)
        return redisTemplate.opsForZSet().score(processingKey(eventId), userId.toString()) != null;
    }

    /**
     * queue -> processing 이동
     * - Queue ZSet: (Member=userId, Score=ticket) 제거
     * - Processing ZSet: (Member=userId, Score=now) 추가 -> 타임아웃 용도
     * - Processing Ticket Hash: (Field=userId, Value=ticket) 추가 -> 티켓번호 보존 용도
     * - 반환값: [userId, ticket, userId, ticket ...]
     */
    public List<String> popToProcessing(Long eventId, int batchSize) {
        String qKey = queueKey(eventId);
        String pKey = processingKey(eventId);
        String tKey = processingRankKey(eventId); // Ticket Hash Key

        String lua = """
            local qKey = KEYS[1]
            local pKey = KEYS[2]
            local tKey = KEYS[3]
            local count = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])

            -- 1. 대기열 상위 N명 조회 (값, 점수 함께 조회)
            -- returns {u1, ticket1, u2, ticket2, ...}
            local members = redis.call('ZRANGE', qKey, 0, count - 1, 'WITHSCORES')
            if #members == 0 then
                return {}
            end

            -- 2. Processing 이동 (ZSet + Hash 분리 저장)
            for i = 1, #members, 2 do
                local user = members[i]
                local ticket = members[i+1] -- 원래 점수(번호표)
                
                -- A. Queue 제거
                redis.call('ZREM', qKey, user)
                
                -- B. Processing ZSet 추가 (Score = Timestamp)
                redis.call('ZADD', pKey, now, user)
                
                -- C. Ticket Hash 저장 (Key = User, Val = Ticket)
                redis.call('HSET', tKey, user, ticket)
            end

            -- 3. 결과 반환 (Java 단에서 Dispatcher로 넘기기 위함)
            return members
        """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);

        List<String> result = redisTemplate.execute(
                script,
                List.of(qKey, pKey, tKey),
                String.valueOf(batchSize),
                String.valueOf(System.currentTimeMillis())
        );

        if (result == null) return Collections.emptyList();
        return result;
    }

    /**
     * processing 에서 완료된 user 제거 (issuer-worker 가 처리 완료 후 콜백할 때 호출)
     * - Processing ZSet 에서 제거
     * - Ticket Hash 에서도 제거
     */
    public void removeFromProcessing(Long eventId, Long userId) {
        String pKey = processingKey(eventId);
        String tKey = processingRankKey(eventId);
        String val  = userId.toString();

        String lua = """
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('HDEL', KEYS[2], ARGV[1])
        """;

        DefaultRedisScript<Object> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Object.class);

        redisTemplate.execute(script, List.of(pKey, tKey), val);
    }

    /**
     * processing 에 오래 머무른 요청을 queue 로 되돌림
     * - issuer-worker 장애 등으로 콜백이 오지 않은 경우의 재시도 안전망
     * - timeoutMs 이상 경과한 항목을 원래 순번(ticket)을 보존하여 재큐
     * - maxRequeue: 한 번에 너무 많이 되돌리지 않기 위한 제한
     *
     * @return 재큐된 항목 수
     */
    public int requeueStaleProcessing(Long eventId, long timeoutMs, int maxRequeue) {
        String pKey = processingKey(eventId);
        String qKey = queueKey(eventId);
        String tKey = processingRankKey(eventId);
        String rKey = queueRankKey(eventId);

        String lua = """
            local cutoff     = tonumber(ARGV[1])
            local maxRequeue = tonumber(ARGV[2])

            -- processing ZSet 에서 오래된 항목 조회 (Score = 입장 timestamp)
            local stale = redis.call('ZRANGEBYSCORE', KEYS[1], 0, cutoff, 'LIMIT', 0, maxRequeue)
            if #stale == 0 then return 0 end

            for _, user in ipairs(stale) do
                -- 원래 ticket 번호 복원 (없으면 새 번호 발급)
                local ticket = redis.call('HGET', KEYS[3], user)
                if not ticket then
                    ticket = redis.call('INCR', KEYS[4])
                end

                -- processing 에서 제거
                redis.call('ZREM', KEYS[1], user)
                redis.call('HDEL', KEYS[3], user)

                -- queue 에 재삽입 (원래 순번 유지)
                redis.call('ZADD', KEYS[2], ticket, user)
            end

            return #stale
        """;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);

        long cutoff = System.currentTimeMillis() - timeoutMs;
        Long requeued = redisTemplate.execute(
                script,
                List.of(pKey, qKey, tKey, rKey),
                String.valueOf(cutoff),
                String.valueOf(maxRequeue)
        );

        return requeued == null ? 0 : requeued.intValue();
    }

    /**
     * 대기열 상위 N명을 Active Queue 로 이동
     * - Queue ZSet 에서 제거
     * - Active Key (gate:active:{eventId}:{userId}) 를 TTL 과 함께 SET
     * - issuer-api-app 이 Active Key 존재 여부로 발급 허용 여부를 판단
     *
     * @return 이동된 userId 목록
     */
    public List<Long> popToActive(Long eventId, int batchSize, long ttlSeconds) {
        String qKey = queueKey(eventId);
        String activePrefix = ACTIVE_KEY_PREFIX + eventId + ":";

        String lua = """
            local qKey        = KEYS[1]
            local count       = tonumber(ARGV[1])
            local ttl         = tonumber(ARGV[2])
            local activePrefix = ARGV[3]

            -- 1. 대기열 상위 N명 조회
            local members = redis.call('ZRANGE', qKey, 0, count - 1)
            if #members == 0 then
                return {}
            end

            -- 2. 대기열에서 제거 + Active Key SET (TTL 포함)
            for _, user in ipairs(members) do
                redis.call('ZREM', qKey, user)
                redis.call('SET', activePrefix .. user, '1', 'EX', ttl)
            end

            return members
        """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);

        List<String> result = redisTemplate.execute(
                script,
                List.of(qKey),
                String.valueOf(batchSize),
                String.valueOf(ttlSeconds),
                activePrefix
        );

        if (result == null) return Collections.emptyList();
        return result.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    /**
     * Active Queue 존재 여부 확인 (read-only)
     * - gate-app 의 getStatus 에서 사용
     */
    public boolean isActive(Long eventId, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_KEY_PREFIX + eventId + ":" + userId));
    }
}
