package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.CouponIssueLog;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CouponIssueLogRepository extends JpaRepository<CouponIssueLog, Long> {

    Optional<CouponIssueLog> findByUserIdAndCouponIdAndRequestTime(Long userId, Long couponId, LocalDateTime requestTime);
}
