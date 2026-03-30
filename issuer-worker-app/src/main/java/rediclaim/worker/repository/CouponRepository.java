package rediclaim.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rediclaim.worker.domain.Coupon;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 재고가 남아있는 경우에만 1 차감 (원자적 UPDATE)
     * 반환값: 실제로 업데이트된 row 수 (1=성공, 0=재고없음)
     */
    @Modifying
    @Query("UPDATE Coupon c SET c.remainingCount = c.remainingCount - 1 WHERE c.id = :id AND c.remainingCount > 0")
    int decrementIfInStock(@Param("id") Long id);
}