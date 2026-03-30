package rediclaim.issuer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.issuer.controller.dto.IssueResponse;
import rediclaim.issuer.repository.ActiveQueueRedisRepository;
import rediclaim.issuer.service.CouponIssueService;
import rediclaim.issuer.service.IssueResult;

@RestController
@RequiredArgsConstructor
public class IssuerController {

    private final CouponIssueService couponIssueService;
    private final ActiveQueueRedisRepository activeQueueRedisRepository;

    /**
     * 쿠폰 발급 요청
     *
     * <p>gate-app 의 폴링 결과가 ACTIVE 인 클라이언트만 이 엔드포인트를 호출한다.
     * Active Queue 검증은 DB 트랜잭션 외부에서 수행하여 불필요한 커넥션 점유를 방지한다.</p>
     *
     * @param eventId  이벤트 ID (couponId 와 1:1 매핑)
     * @param userId   발급 요청 유저 ID
     * @return SUCCESS / NOT_IN_ACTIVE_QUEUE / ALREADY_ISSUED / OUT_OF_STOCK
     */
    @PostMapping("/issue/events/{eventId}")
    public IssueResponse issue(@PathVariable Long eventId, @RequestParam Long userId) {
        // Active Queue 검증 (트랜잭션 외부) — DB 커넥션 획득 전 선제 차단
        if (!activeQueueRedisRepository.isActive(eventId, userId)) {
            return new IssueResponse(IssueResult.NOT_IN_ACTIVE_QUEUE.name());
        }
        IssueResult result = couponIssueService.issue(eventId, userId);
        return new IssueResponse(result.name());
    }
}