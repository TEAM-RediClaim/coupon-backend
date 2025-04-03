package rediclaim.couponbackend.controller.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCouponRequest {

    private Long adminId;

    private Long adminCode;

    private String couponName;      // 발급할 쿠폰의 이름

    private int quantity;       // 발급할 쿠폰의 수량
}
