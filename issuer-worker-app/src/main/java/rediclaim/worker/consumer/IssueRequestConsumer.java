package rediclaim.worker.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import rediclaim.worker.service.CouponIssueWorkerService;
import rediclaim.worker.service.GateCallbackService;
import rediclaim.worker.service.IssueResult;

@Slf4j
@Component
@RequiredArgsConstructor
public class IssueRequestConsumer {

    private final CouponIssueWorkerService couponIssueWorkerService;
    private final GateCallbackService gateCallbackService;

    /**
     * gate-app 이 Kafka 에 발행한 쿠폰 발급 요청을 consume 한다.
     *
     * <p>처리 결과(SUCCESS / ALREADY_ISSUED / OUT_OF_STOCK) 와 무관하게
     * gate-app 에 콜백을 보내 processing 상태에서 해당 유저를 제거한다.
     * 예외 발생 시에는 ack 를 보내지 않아 Kafka 가 재처리한다.</p>
     */
    @KafkaListener(
            topics = "${worker.kafka-topic}",
            groupId = "${worker.consumer-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(IssueRequestMessage message, Acknowledgment ack) {
        Long eventId = message.eventId();   // eventId = couponId (1:1 매핑 정책)
        Long userId  = message.userId();

        try {
            IssueResult result = couponIssueWorkerService.issueCoupon(userId, eventId);
            log.info("Coupon issue result={} userId={} couponId={}", result, userId, eventId);

            gateCallbackService.notifyCompleted(eventId, userId);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Coupon issue failed. userId={} couponId={}", userId, eventId, e);
            // ack 하지 않음 → Kafka 재시도 (at-least-once 보장)
        }
    }
}
