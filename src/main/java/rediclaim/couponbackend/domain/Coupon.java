package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int remainingCount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User creator;

    @Builder
    private Coupon(String name, int remainingCount, User creator) {
        this.name = name;
        this.remainingCount = remainingCount;
        this.creator = creator;
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
