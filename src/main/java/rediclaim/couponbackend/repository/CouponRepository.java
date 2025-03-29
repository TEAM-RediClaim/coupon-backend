package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.Coupon;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {


}
