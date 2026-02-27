package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.UserCouponService;

@RestController
@RequiredArgsConstructor
public class UserCouponController {

    private final UserCouponService userCouponService;

    @GetMapping("/api/users/{userId}/coupons")
    public BaseResponse<IssuedCouponsResponse> showAllIssuedCoupons(@PathVariable Long userId) {
        return BaseResponse.ok(userCouponService.showAllIssuedCoupons(userId));
    }
}