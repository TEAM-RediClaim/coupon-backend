package rediclaim.worker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import rediclaim.couponbackend.domain.BaseEntity;
import rediclaim.couponbackend.domain.User;

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
}
