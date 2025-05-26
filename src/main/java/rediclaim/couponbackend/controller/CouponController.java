package rediclaim.couponbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.CreateCouponRequest;
import rediclaim.couponbackend.controller.request.IssueCouponRequest;
import rediclaim.couponbackend.controller.response.CreateCouponResponse;
import rediclaim.couponbackend.controller.response.LogRecord;
import rediclaim.couponbackend.controller.response.ValidCouponsResponse;
import rediclaim.couponbackend.controller.response.VerificationLogsResponse;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.CouponService;
import rediclaim.couponbackend.service.OrderVerificationService;

import java.util.List;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.REQUEST_VALIDATION_FAILED;
import static rediclaim.couponbackend.global.util.BindingResultUtils.getErrorMessage;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;
    private final OrderVerificationService orderVerificationService;

    @PostMapping("/api/coupons/{couponId}")
    public BaseResponse<Void> issueCoupon(@Valid @RequestBody IssueCouponRequest request, @PathVariable Long couponId, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long requestSequence = orderVerificationService.assignRequestOrder(request.getUserId(), couponId);
        couponService.issueCoupon(request.getUserId(), couponId);
        orderVerificationService.recordCompletion(request.getUserId(), couponId, requestSequence);

        return BaseResponse.ok(null);
    }

    @GetMapping("/api/coupons")
    public BaseResponse<ValidCouponsResponse> showAllValidCoupons() {
        return BaseResponse.ok(couponService.showAllValidCoupons());
    }

    @PostMapping("/api/coupons")
    public BaseResponse<CreateCouponResponse> createCoupon(@Valid @RequestBody CreateCouponRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long couponId = couponService.createCoupon(request.getCreatorId(), request.getQuantity(), request.getCouponName());
        return BaseResponse.ok(CreateCouponResponse.builder()
                        .couponId(couponId)
                        .build());
    }

    @GetMapping("/api/coupons/{couponId}/verification-logs")
    public BaseResponse<VerificationLogsResponse> getVerificationLogs(@PathVariable Long couponId) {
        List<LogRecord> completionLogs = orderVerificationService.getCompletionLogs(couponId);

        return BaseResponse.ok(VerificationLogsResponse.builder()
                .completions(completionLogs)
                .build());
    }
}
