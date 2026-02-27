package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.UserCoupon;

import java.util.List;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(Long userId);

    @Transactional
    @Modifying
    @Query(
            value = "INSERT INTO user_coupon (user_id, coupon_id, created_date_time, modified_date_time) VALUES (:userId, :couponId, NOW(), NOW())",
            nativeQuery = true
    )
    void insertUserCoupon(@Param("userId") Long userId, @Param("couponId") Long couponId);
}
