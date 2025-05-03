package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.CouponIssueLog;

@Repository
public interface CouponIssueLogRepository extends JpaRepository<CouponIssueLog, Long> {

}
