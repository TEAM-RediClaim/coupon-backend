package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class UserCoupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    private Coupon coupon;

    private UserCoupon(User user, Coupon coupon) {
        this.user = user;
        this.coupon = coupon;
    }

    public static UserCoupon create(User user, Coupon coupon) {
        return new UserCoupon(user, coupon);
    }

}
