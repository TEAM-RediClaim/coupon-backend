package rediclaim.couponbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.CreateCouponRequest;
import rediclaim.couponbackend.controller.request.IssueCouponRequest;
import rediclaim.couponbackend.controller.response.CreateCouponResponse;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.CouponService;

import static rediclaim.couponbackend.global.util.BindingResultUtils.getErrorMessage;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/coupons/{couponId}")
    public BaseResponse<Void> issueCoupon(@Valid @RequestBody IssueCouponRequest request, @PathVariable Long couponId, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(getErrorMessage(bindingResult));
        }

        couponService.issueCoupon(request.getUserId(), couponId);
        return BaseResponse.ok(null);
    }

    @GetMapping("/api/coupons")
    public BaseResponse<ValidCouponsResponse> showAllValidCoupons() {
        return BaseResponse.ok(couponService.showAllValidCoupons());
    }

    @PostMapping("/api/coupons")
    public BaseResponse<CreateCouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(getErrorMessage(bindingResult));
        }

        Long couponId = couponService.createCoupon(request.getCreatorId(), request.getQuantity(), request.getCouponName());
        return BaseResponse.ok(CreateCouponResponse.builder()
                        .couponId(couponId)
                        .build());
    }
}
