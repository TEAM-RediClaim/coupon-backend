package rediclaim.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import rediclaim.worker.config.WorkerProperties;

/**
 * 쿠폰 발급 처리 완료 후 gate-app 에 콜백을 보내
 * processing 상태에서 해당 유저를 제거하도록 요청한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GateCallbackService {

    private final RestClient restClient;
    private final WorkerProperties workerProperties;

    public void notifyCompleted(Long eventId, Long userId) {
        try {
            restClient.post()
                    .uri(workerProperties.getGateBaseUrl()
                            + "/gate/events/" + eventId + "/processing/complete"
                            + "?userId=" + userId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 콜백 실패는 치명적이지 않음.
            // gate-app 의 requeueStaleProcessing 스케줄러가 타임아웃 후 처리.
            log.warn("Gate callback failed. eventId={}, userId={} : {}", eventId, userId, e.getMessage());
        }
    }
}