package rediclaim.gate.dispatcher;

import rediclaim.gate.controller.dto.GateStatusResponse;

/**
 * 대기열(Waiting Queue) → 처리 단계 이동 전략.
 *
 * <p>구현체는 {@code gate.dispatch-mode} 설정값에 따라 결정된다.</p>
 * <ul>
 *   <li>{@code kafka}        : 대기열 → Processing Queue → Kafka 발행 (issuer-worker-app 소비)</li>
 *   <li>{@code active-queue} : 대기열 → Active Queue (TTL) → 클라이언트가 issuer-api-app 직접 호출</li>
 * </ul>
 */
public interface DispatchStrategy {

    /**
     * 대기열에서 N명을 꺼내 다음 단계로 이동시킨다.
     *
     * @return 처리된 유저 수
     */
    int dispatch(Long eventId);

    /**
     * 대기열에 없는 유저의 현재 상태를 반환한다.
     * (GateService 에서 WAITING 여부 확인 후 이 메서드에 위임)
     *
     * @return PROCESSING / ACTIVE / UNKNOWN 중 하나
     */
    GateStatusResponse statusOf(Long eventId, Long userId);

    /**
     * Processing 상태에서 타임아웃된 요청을 대기열로 되돌린다.
     *
     * <p>kafka 모드에서만 의미 있는 동작이며, active-queue 모드에서는 기본적으로 no-op이다.</p>
     *
     * @return 재큐된 항목 수
     */
    default int requeueStale(Long eventId) {
        return 0;
    }
}
