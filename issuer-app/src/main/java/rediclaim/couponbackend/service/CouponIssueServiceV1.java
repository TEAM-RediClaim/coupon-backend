package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupon;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

/**
 * [TASK 1] 순수 DB 기반 쿠폰 발급 서비스 (Redis / Kafka 미사용)
 *
 * <p>두 가지 발급 방식을 제공한다:</p>
 * <ul>
 *   <li>{@link #issueWithoutLock} — 락 없이 단순 조회·차감. 멀티 스레드 환경에서 동시성 이슈 발생.</li>
 *   <li>{@link #issueWithPessimisticLock} — 비관적 락(SELECT ... FOR UPDATE) 적용. 초과 발급 방지.</li>
 * </ul>
 *
 * <h3>비관적 락 vs 낙관적 락 선택 근거</h3>
 * <p>선착순 쿠폰 발급은 다수 유저가 동시에 동일한 쿠폰 행에 쓰기 요청을 보내는 <b>고충돌(high-contention)</b> 시나리오다.</p>
 * <ul>
 *   <li><b>낙관적 락</b>: 충돌 발생 시 {@code ObjectOptimisticLockingFailureException} → 재시도 필요.
 *       고트래픽에서 재시도가 폭발적으로 증가(retry storm)해 DB 부하가 오히려 악화될 수 있다.</li>
 *   <li><b>비관적 락</b>: 충돌 발생 시 대기(wait) → 직렬화된 순서로 처리.
 *       재시도 로직 없이도 정확한 선착순이 보장되므로 이 시나리오에 더 적합하다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CouponIssueServiceV1 {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;

    /**
     * [락 없음] 단순 DB 조회 후 재고 차감
     *
     * <p>Read → Check → Write 흐름이 원자적이지 않아 TOCTOU(Time-Of-Check-Time-Of-Use) 취약점이 존재한다.
     * 여러 스레드가 동시에 {재고 > 0} 조건을 통과한 뒤 각자 재고를 차감하면
     * 실제 발급 수량이 재고를 초과하는 <b>초과 발급(overselling)</b>이 발생한다.</p>
     */
    @Transactional
    public void issueWithoutLock(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));

        validateIssuable(user, coupon);

        coupon.decrementRemainingCount();
        userCouponRepository.save(UserCoupon.builder().user(user).coupon(coupon).build());
    }

    /**
     * [비관적 락] SELECT ... FOR UPDATE 로 쿠폰 행에 배타적 잠금 후 재고 차감
     *
     * <p>트랜잭션이 커밋(또는 롤백)될 때까지 다른 트랜잭션은 해당 쿠폰 행을 읽거나 쓸 수 없다.
     * 결과적으로 재고 조회·차감이 직렬화되어 초과 발급이 원천적으로 차단된다.</p>
     *
     * <p><b>주의</b>: 동시에 많은 트랜잭션이 락을 기다리면 처리량(throughput)이 낮아진다.
     * 이 한계는 이후 TASK 2에서 Redis 원자 연산으로 개선한다.</p>
     */
    @Transactional
    public void issueWithPessimisticLock(Long userId, Long couponId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        // SELECT ... FOR UPDATE: 해당 쿠폰 행에 배타적 잠금 획득
        Coupon coupon = couponRepository.findByIdForUpdate(couponId)
                .orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));

        validateIssuable(user, coupon);

        coupon.decrementRemainingCount();
        userCouponRepository.save(UserCoupon.builder().user(user).coupon(coupon).build());
    }

    @Transactional
    public Long createCoupon(Long creatorId, int quantity, String couponName) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        if (!creator.isCreator()) {
            throw new CustomException(USER_NOT_ALLOWED_TO_CREATE_COUPON);
        }

        Coupon saved = couponRepository.save(Coupon.builder()
                .name(couponName)
                .remainingCount(quantity)
                .creator(creator)
                .build());

        return saved.getId();
    }

    private void validateIssuable(User user, Coupon coupon) {
        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new CustomException(USER_ALREADY_HAS_COUPON);
        }
        if (!coupon.hasRemainingStock()) {
            throw new CustomException(COUPON_OUT_OF_STOCK);
        }
    }
}
