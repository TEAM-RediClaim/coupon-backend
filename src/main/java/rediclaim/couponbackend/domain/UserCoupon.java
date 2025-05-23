package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "userCoupon",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_coupon",
                        columnNames = {"user_id", "coupon_id"}
                )
        }
)
public class UserCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Builder
    private UserCoupon(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
    }
}
