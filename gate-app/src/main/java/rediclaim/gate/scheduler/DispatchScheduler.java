package rediclaim.gate.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.service.GateService;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchScheduler {

    private final GateService gateService;
    private final GateProperties gateProperties;

    /**
     * 설정된 주기(gate.dispatch-interval-ms)마다 각 event별로 queue -> active queue로 이동
     * - rate 설정에 따라 N명씩 처리
     */
    @Scheduled(fixedDelayString = "${gate.dispatch-interval-ms:3000}")
    public void dispatchQueueToProcessing() {
        for (Long eventId : gateProperties.getEventIds()) {
            try {
                int dispatched = gateService.dispatchOnce(eventId);
                if (dispatched > 0) {
                    log.debug("Event {} dispatched {} users to processing", eventId, dispatched);
                }
            } catch (Exception e) {
                log.error("Failed to dispatch for event {}", eventId, e);
            }
        }
    }

    /**
     * 30초마다 processing 에 오래 머문 요청을 queue 로 되돌림
     * - issuer-worker 장애 등으로 콜백이 오지 않은 경우의 복구 안전망
     */
    @Scheduled(fixedDelayString = "${gate.stale-requeue-interval-ms:30000}")
    public void requeueStaleRequests() {
        for (Long eventId : gateProperties.getEventIds()) {
            try {
                int requeued = gateService.requeueStaleProcessing(eventId);
                if (requeued > 0) {
                    log.warn("Event {} requeued {} stale processing requests", eventId, requeued);
                }
            } catch (Exception e) {
                log.error("Failed to requeue stale processing for event {}", eventId, e);
            }
        }
    }
}
