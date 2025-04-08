package rediclaim.couponbackend.controller.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCouponRequest {

    private Long creatorId;

    private String couponName;

    private int quantity;
}
