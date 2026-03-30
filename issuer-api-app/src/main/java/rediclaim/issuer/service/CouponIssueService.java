package rediclaim.issuer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.issuer.repository.CouponRepository;
import rediclaim.issuer.repository.UserCouponRepository;

/**
 * [TASK 3 - Active Queue 방식] 쿠폰 발급 서비스
 *
 * <h3>발급 흐름</h3>
 * <ol>
 *   <li>중복 발급 확인: DB UNIQUE(user_id, coupon_id) 의 사전 방어.</li>
 *   <li>재고 원자적 차감: {@code UPDATE WHERE remaining_count > 0}</li>
 *   <li>발급 이력 INSERT</li>
 * </ol>
 *
 * <p>Active Queue 검증은 Controller 에서 트랜잭션 외부에 수행한다.
 * 이 서비스는 Active Queue 검증을 통과한 요청만 수신하며, DB 작업만 담당한다.</p>
 *
 * <p>발급 완료 후에도 TTL 만료 전까지 Active Queue 키가 남아 있을 수 있으나,
 * 재요청 시 DB UNIQUE constraint 가 중복 발급을 방지한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CouponIssueService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public IssueResult issue(Long eventId, Long userId) {
        // ── Step 1. 중복 발급 확인 ────────────────────────────────────────────
        if (userCouponRepository.existsByUser_IdAndCoupon_Id(userId, eventId)) {
            return IssueResult.ALREADY_ISSUED;
        }

        // ── Step 2. 재고 원자적 차감 ──────────────────────────────────────────
        int affected = couponRepository.decrementIfInStock(eventId);
        if (affected == 0) {
            return IssueResult.OUT_OF_STOCK;
        }

        // ── Step 3. 발급 이력 INSERT ──────────────────────────────────────────
        // DB UNIQUE(user_id, coupon_id) 가 최후 보호막
        userCouponRepository.insertUserCoupon(userId, eventId);

        return IssueResult.SUCCESS;
    }
}