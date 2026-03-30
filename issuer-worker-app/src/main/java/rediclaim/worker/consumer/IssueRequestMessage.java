package rediclaim.worker.consumer;

/**
 * gate-app 의 IssueRequestMessage 와 동일한 구조.
 * 모듈 간 결합을 피하기 위해 로컬에 복사하여 사용.
 * eventId = couponId (1:1 매핑 정책)
 */
public record IssueRequestMessage(
        Long eventId,
        Long userId,
        Long rank
) {
}
