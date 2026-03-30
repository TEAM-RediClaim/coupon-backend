package rediclaim.gate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "gate")
public class GateProperties {

    private List<Long> eventIds;

    private String dispatchMode;

    private int dispatchQuantity;

    private String kafkaTopic;

    /** processing 상태에서 이 시간(ms) 이상 머물면 stale 로 판단하여 queue 로 되돌림 */
    private long staleTimeoutMs = 60_000;

    /** requeueStaleProcessing 한 번 실행 시 최대 재큐 수 */
    private int maxRequeue = 200;

    /** Active Queue TTL (초) - TTL 경과 시 Redis 자동 제거 */
    private long activeTtlSeconds = 60;

    /** DispatchScheduler 실행 주기 (ms) */
    private long dispatchIntervalMs = 3000;

    /** requeueStaleProcessing 실행 주기 (ms) - kafka 모드 전용 */
    private long staleRequeueIntervalMs = 30000;
}
