package rediclaim.worker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.worker.domain.UserCoupon;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    /**
     * 발급 이력이 이미 존재하는지 확인 (Kafka at-least-once 재처리 대응)
     */
    boolean existsByUser_IdAndCoupon_Id(Long userId, Long couponId);

    /**
     * 엔티티 로드 없이 직접 INSERT (최소 커넥션 점유)
     */
    @Transactional
    @Modifying
    @Query(
            value = "INSERT INTO user_coupon (user_id, coupon_id, created_date_time, modified_date_time) VALUES (:userId, :couponId, NOW(), NOW())",
            nativeQuery = true
    )
    void insertUserCoupon(@Param("userId") Long userId, @Param("couponId") Long couponId);
}