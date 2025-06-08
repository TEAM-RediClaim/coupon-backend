package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupon;
import rediclaim.couponbackend.domain.event.CouponIssueEvent;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.COUPON_NOT_FOUND;
import static rediclaim.couponbackend.exception.ExceptionResponseStatus.USER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueConsumer {

    private final UserCouponRepository userCouponRepository;
    private final UserRepository userRepository;
    private final CouponRepository couponRepository;

    @KafkaListener(topics = "coupons")
    @Transactional
    public void handleCouponIssue(CouponIssueEvent event) {
        Long userId = event.getUserId();
        Long couponId = event.getCouponId();
        log.info("CouponIssueEvent 수신 : userId={}, couponId={}", userId, couponId);

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CustomException(COUPON_NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));

        try {
            // UserCoupon save
            userCouponRepository.save(
                    UserCoupon.builder()
                            .user(user)
                            .coupon(coupon)
                            .build()
            );

            log.info("CouponIssueEvent 처리 완료 : userId={}, couponId={}", userId, couponId);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateUserCouponException(e)) {
                log.warn("중복 CouponIssueEvent 수신 -> 무시합니다. : userId={}, couponId={}", userId, couponId);
            } else {
                // 그 외 제약 위반은 재시도 or DLT 로
                log.error("DataIntegrityViolationException 발생 (unique 제약 아님). 재시도 예정 : userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("알 수 없는 예외 발생. 재시도 예정 : userId={}, couponId={}, error={}", userId, couponId, e.getMessage());
            throw e;
        }
    }

    private boolean isDuplicateUserCouponException(DataIntegrityViolationException e) {
        Throwable cause = e.getCause();
        return cause != null
                && cause.getMessage() != null
                && cause.getMessage().contains("Duplicate entry");      // mysql unique 제약 조건 위반 예외 메시지
    }
}
