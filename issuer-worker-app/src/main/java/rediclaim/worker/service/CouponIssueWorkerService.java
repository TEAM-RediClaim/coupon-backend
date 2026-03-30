package rediclaim.worker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.worker.repository.CouponRepository;
import rediclaim.worker.repository.UserCouponRepository;

/**
 * [TASK 3] DB를 SSOT로 하는 쿠폰 발급 서비스
 *
 * <h3>TASK 2(Redis Lua) 대비 차이점</h3>
 * <ul>
 *   <li>Redis 없음. 재고 관리·중복 방지 모두 DB에서 처리.</li>
 *   <li>재고 차감: UPDATE WHERE remaining_count > 0 (원자적, 락 없음)</li>
 *   <li>중복 방지: UNIQUE constraint (user_id, coupon_id) + 사전 존재 확인</li>
 * </ul>
 *
 * <h3>트래픽 부하 관리</h3>
 * <p>gate-app 이 대기열로 트래픽을 흡수하고 제어된 속도로 Kafka 에 발행하므로
 * 이 서비스는 DB 가 감당할 수 있는 수준의 트래픽만 받는다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponIssueWorkerService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public IssueResult issueCoupon(Long userId, Long couponId) {

        // ── Step 1. 중복 발급 확인 (Kafka at-least-once 재처리 대응) ─────────
        if (userCouponRepository.existsByUser_IdAndCoupon_Id(userId, couponId)) {
            log.debug("Already issued. userId={}, couponId={}", userId, couponId);
            return IssueResult.ALREADY_ISSUED;
        }

        // ── Step 2. 재고 원자적 차감 (remaining_count > 0 조건 포함) ──────────
        int affected = couponRepository.decrementIfInStock(couponId);
        if (affected == 0) {
            log.debug("Out of stock. couponId={}", couponId);
            return IssueResult.OUT_OF_STOCK;
        }

        // ── Step 3. 발급 이력 INSERT ─────────────────────────────────────────
        // UNIQUE constraint (user_id, coupon_id) 이 최후 보호막.
        userCouponRepository.insertUserCoupon(userId, couponId);
        log.debug("Issued. userId={}, couponId={}", userId, couponId);

        return IssueResult.SUCCESS;
    }
}