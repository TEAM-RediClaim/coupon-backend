package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Getter
public class ValidCouponsResponse {

    private List<ValidCoupon> validCoupons;
}
