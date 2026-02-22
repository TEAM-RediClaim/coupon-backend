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
import rediclaim.couponbackend.service.CouponIssueServiceV1;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.REQUEST_VALIDATION_FAILED;
import static rediclaim.couponbackend.global.util.BindingResultUtils.getErrorMessage;

/**
 * [TASK 1] 순수 DB 비관적 락 기반 쿠폰 API
 *
 * <p>Redis / Kafka 없이 단순 DB 트랜잭션으로 처리한다.
 * 부하 테스트를 통해 단일 서버 TPS 기준값을 측정하고,
 * 애플리케이션 스케일 아웃 후 TPS 변화를 비교하기 위한 엔드포인트.</p>
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponControllerV1 {

    private final CouponIssueServiceV1 couponIssueServiceV1;

    @PostMapping
    public BaseResponse<CreateCouponResponse> createCoupon(
            @Valid @RequestBody CreateCouponRequest request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long couponId = couponIssueServiceV1.createCoupon(
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

        couponIssueServiceV1.issueWithPessimisticLock(request.getUserId(), couponId);
        return BaseResponse.ok(null);
    }
}
