package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.CouponIssueLog;
import rediclaim.couponbackend.repository.CouponIssueLogRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponIssueLogService {

    private static final Logger log = LogManager.getLogger(CouponIssueLogService.class);
    private final CouponIssueLogRepository logRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logRequest(Long userId, Long couponId) {
        CouponIssueLog log = CouponIssueLog.builder()
                .userId(userId)
                .couponId(couponId)
                .requestTime(LocalDateTime.now())
                .issueStatus(false)
                .attemptCount(0)
                .isLastAttempt(false)
                .build();
        return logRepository.save(log).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSuccess(Long id) {
        logRepository.findById(id).ifPresent(log -> {
            log.setIssueTime(LocalDateTime.now());
            log.changeIssueStatus(true);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAttemptCount(Long id, int attemptCount) {
        logRepository.findById(id).ifPresent(log -> log.setAttemptCount(attemptCount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markLastAttempt(Long id, int attemptCount) {
        logRepository.findById(id).ifPresent(log -> {
            log.setAttemptCount(attemptCount);
            log.markLastAttempt();
        });
    }
}
