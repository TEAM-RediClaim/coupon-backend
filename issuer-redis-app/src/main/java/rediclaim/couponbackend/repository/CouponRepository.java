package rediclaim.couponbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rediclaim.couponbackend.domain.Coupon;

/**
 * [TASK 2] 쿠폰 레포지토리
 *
 * <p>TASK 1 의 {@code @Lock(PESSIMISTIC_WRITE)} 쿼리가 제거되었다.
 * 동시성 제어는 Redis Lua 스크립트가 담당하므로 DB 는 락 없이 단순 쓰기만 수행한다.</p>
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /**
     * 재고 1 감소 (락 없는 직접 UPDATE).
     *
     * <p>Redis Lua 가 이미 재고 0 이하로 내려가지 않음을 보장하므로
     * WHERE remaining_count > 0 조건 없이 단순 차감해도 안전하다.</p>
     *
     * <p>{@code clearAutomatically = true} : 벌크 UPDATE 후 영속성 컨텍스트를 초기화해
     * 이전에 로드된 Coupon 엔티티의 stale 상태를 방지한다.</p>
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Coupon c SET c.remainingCount = c.remainingCount - 1 WHERE c.id = :id")
    void decrementRemainingCount(@Param("id") Long id);
}