package rediclaim.couponbackend.repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.Coupon;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
//    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})      // 3초 타임아웃
    // -> MySQL InnoDB는 query hint 인식하지 못함
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);
}
