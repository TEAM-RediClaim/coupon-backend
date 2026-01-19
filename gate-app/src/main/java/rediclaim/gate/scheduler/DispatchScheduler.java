package rediclaim.gate.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rediclaim.gate.config.GateProperties;
import rediclaim.gate.service.GateService;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchScheduler {

    private final GateService gateService;
    private final GateProperties gateProperties;

    /**
     * 3초마다 각 event별로 queue -> processing으로 이동
     * - rate 설정에 따라 N명씩 처리
     */
    @Scheduled(fixedDelay = 3000, timeUnit = TimeUnit.MILLISECONDS)
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
}
