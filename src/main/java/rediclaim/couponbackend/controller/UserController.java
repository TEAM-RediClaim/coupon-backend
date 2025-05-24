package rediclaim.couponbackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.RegisterCreatorRequest;
import rediclaim.couponbackend.controller.request.RegisterUserRequest;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.controller.response.RegisterCreatorResponse;
import rediclaim.couponbackend.controller.response.RegisterUserResponse;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.UserService;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.REQUEST_VALIDATION_FAILED;
import static rediclaim.couponbackend.global.util.BindingResultUtils.getErrorMessage;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/{userId}/coupons")
    public BaseResponse<IssuedCouponsResponse> showAllIssuedCoupons(@PathVariable Long userId) {
        return BaseResponse.ok(userService.showAllIssuedCoupons(userId));
    }

    @PostMapping("/api/users")
    public BaseResponse<RegisterUserResponse> registerUser(@Valid @RequestBody RegisterUserRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long userId = userService.registerUser(request.getName());
        return BaseResponse.ok(RegisterUserResponse.builder()
                .userId(userId)
                .build());
    }

    @PostMapping("/api/creators")
    public BaseResponse<RegisterCreatorResponse> registerCreator(@Valid @RequestBody RegisterCreatorRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            throw new CustomException(REQUEST_VALIDATION_FAILED, getErrorMessage(bindingResult));
        }

        Long creatorId = userService.registerCreator(request.getName());
        return BaseResponse.ok(RegisterCreatorResponse.builder()
                .creatorId(creatorId)
                .build());
    }

}
