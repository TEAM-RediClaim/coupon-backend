package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.controller.response.ValidCoupon;
import rediclaim.couponbackend.domain.*;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;
import java.util.Objects;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final CouponIssueLogService logService;

    /**
     * 유저가 발급한 적이 없는 쿠폰이고, 재고가 있을 경우 해당 유저에게 쿠폰을 발급해준다
     */
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            notRecoverable = CustomException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 10)
    )
    @Transactional
    public void issueCoupon(Long userId, Long couponId, Long logId) {
        // 현재 쿠폰 발급 시도 횟수 기록
        int attempts = Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1;
        logService.updateAttemptCount(logId, attempts);

        Coupon coupon = couponRepository.findById(couponId).orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));
        User user = userRepository.findById(userId).orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        if (userCouponRepository.existsByUserAndCoupon(user, coupon)) {
            throw new CustomException(USER_ALREADY_HAS_COUPON);
        }

        if (!coupon.hasRemainingStock()) {
            throw new CustomException(COUPON_OUT_OF_STOCK);
        }
        coupon.decrementRemainingCount();
        // 즉시 update & flush
        couponRepository.save(coupon);
        couponRepository.flush();

        userCouponRepository.save(UserCoupon.builder()
                .user(user)
                .coupon(coupon)
                .build());
    }

    @Recover
    public void recoverLockTimeout(OptimisticLockingFailureException exception, Long userId, Long couponId, Long logId) {
        // 마지막 쿠폰 발급 시도 횟수 기록
        int attempts = Objects.requireNonNull(RetrySynchronizationManager.getContext()).getRetryCount() + 1;

        logService.markLastAttempt(logId, attempts);
        throw new CustomException(COUPON_LOCK_TIMEOUT);
    }

    public ValidCouponsResponse showAllValidCoupons() {
        List<Coupon> coupons = couponRepository.findByRemainingCountGreaterThan(0);
        List<ValidCoupon> list = coupons.stream()
                .map(ValidCoupon::of)
                .toList();
        return ValidCouponsResponse.builder()
                .validCoupons(list)
                .build();
    }

    @Transactional
    public Long createCoupon(Long creatorId, int quantity, String couponName) {
        User creator = userRepository.findById(creatorId).orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        if (!creator.isCreator()) {
            throw new CustomException(USER_NOT_ALLOWED_TO_CREATE_COUPON);
        }

        return couponRepository.save(Coupon.builder()
                .name(couponName)
                .remainingCount(quantity)
                .creator(creator)
                .build()).getId();
    }
}
