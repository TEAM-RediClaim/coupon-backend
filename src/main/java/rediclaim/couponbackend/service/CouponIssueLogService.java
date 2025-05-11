package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.CouponIssueLog;
import rediclaim.couponbackend.repository.CouponIssueLogRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CouponIssueLogService {

    private final CouponIssueLogRepository logRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long logRequest(Long userId, Long couponId) {
        CouponIssueLog log = CouponIssueLog.builder()
                .userId(userId)
                .couponId(couponId)
                .requestTime(LocalDateTime.now())
                .issueStatus(false)
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
}
