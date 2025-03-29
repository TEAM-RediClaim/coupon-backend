package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "Coupons")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private int remainingCount;

    @ManyToOne(fetch = FetchType.LAZY)
    private Admin couponCreator;

    @Builder
    private Coupon(String name, int remainingCount, Admin couponCreator) {
        this.name = name;
        this.remainingCount = remainingCount;
        this.couponCreator = couponCreator;
    }

    public boolean isSameCoupon(Long couponId) {
        return id.equals(couponId);
    }

    public boolean hasRemainingStock() {
        return remainingCount > 0;
    }

    public void decrementRemainingCount() {
        if (remainingCount <= 0) {
            throw new IllegalStateException("쿠폰 재고가 부족하여 차감할 수 없습니다.");
        }
        remainingCount--;
    }
}
