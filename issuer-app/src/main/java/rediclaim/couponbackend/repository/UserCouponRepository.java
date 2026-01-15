package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.domain.UserCoupon;

import java.util.List;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserAndCoupon(User user, Coupon coupon);
}
