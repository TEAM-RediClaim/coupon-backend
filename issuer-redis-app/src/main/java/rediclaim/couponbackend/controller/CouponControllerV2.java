package rediclaim.couponbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.CreateCouponRequest;
import rediclaim.couponbackend.controller.request.IssueCouponRequest;
import rediclaim.couponbackend.controller.response.CreateCouponResponse;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.CouponIssueServiceV2;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.REQUEST_VALIDATION_FAILED;
import static rediclaim.couponbackend.global.util.BindingResultUtils.getErrorMessage;

/**
 * [TASK 2] Redis Lua Script 기반 쿠폰 API
 *
 * <p>TASK 1({@code CouponControllerV1})과 동일한 URL을 사용하므로
 * 기존 k6 부하 테스트 스크립트를 수정 없이 재사용할 수 있다.</p>
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponControllerV2 {

    private final CouponIssueServiceV2 couponIssueServiceV2;

    @PostMapping
    public BaseResponse<CreateCouponResponse> createCoupon(
            @Valid @RequestBody CreateCouponRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long couponId = couponIssueServiceV2.createCoupon(
                request.getCreatorId(), request.getQuantity(), request.getCouponName());
        return BaseResponse.ok(CreateCouponResponse.builder().couponId(couponId).build());
    }

    @PostMapping("/{couponId}")
    public BaseResponse<Void> issueCoupon(
            @Valid @RequestBody IssueCouponRequest request,
            @PathVariable Long couponId,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        couponIssueServiceV2.issueCoupon(request.getUserId(), couponId);
        return BaseResponse.ok(null);
    }
}
