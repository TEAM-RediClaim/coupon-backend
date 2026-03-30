package rediclaim.worker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import rediclaim.couponbackend.domain.BaseEntity;
import rediclaim.couponbackend.domain.User;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "user_coupon",
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
}