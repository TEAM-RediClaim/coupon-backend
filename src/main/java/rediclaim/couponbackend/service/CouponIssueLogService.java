package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import rediclaim.couponbackend.domain.CouponIssueLog;
import rediclaim.couponbackend.repository.CouponIssueLogRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponIssueLogService {

    private final CouponIssueLogRepository logRepository;

    @Async
    public void logRequest(Long userId, Long couponId, LocalDateTime requestTime) {
        CouponIssueLog log = CouponIssueLog.builder()
                .userId(userId)
                .couponId(couponId)
                .requestTime(requestTime)
                .issueStatus(false)
                .attemptCount(1)
                .isLastAttempt(false)
                .build();
        logRepository.save(log);
    }

    @Async
    public void logSuccess(Long userId, Long couponId, LocalDateTime requestTime, LocalDateTime issueTime) {
        logRepository.findByUserIdAndCouponIdAndRequestTime(userId, couponId, requestTime)
                .ifPresent(log -> {
                    log.setIssueTime(issueTime);
                    log.changeIssueStatus(true);
                    logRepository.save(log);
        });
    }
}
