package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.IssueCouponRequest;
import rediclaim.couponbackend.controller.response.ValidCoupons;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.CouponService;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/coupons/{couponId}")
    public BaseResponse<Void> issueCoupon(@RequestBody IssueCouponRequest request, @PathVariable Long couponId) {
        couponService.issueCoupon(request.getUserId(), couponId);
        return BaseResponse.ok(null);
    }

    @GetMapping("/api/coupons")
    public BaseResponse<ValidCoupons> showAllValidCoupons() {
        return BaseResponse.ok(couponService.showAllValidCoupons());
    }
}
