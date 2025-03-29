package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rediclaim.couponbackend.controller.request.IssueCouponRequest;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.CouponService;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/coupons/{couponId}")
    public BaseResponse<String> issueCoupon(@RequestBody IssueCouponRequest request, @PathVariable Long couponId) {
        couponService.issueCoupon(request.getUserId(), couponId);
        return BaseResponse.ok("쿠폰 발급에 성공하였습니다.");
    }
}
