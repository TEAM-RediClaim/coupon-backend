package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.RegisterUserRequest;
import rediclaim.couponbackend.controller.response.IssuedCoupons;
import rediclaim.couponbackend.controller.response.RegisterUserResponse;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.UserService;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/{userId}/coupons")
    public BaseResponse<IssuedCoupons> showAllIssuedCoupons(@PathVariable Long userId) {
        return BaseResponse.ok(userService.showAllIssuedCoupons(userId));
    }

    @PostMapping("/api/users")
    public BaseResponse<RegisterUserResponse> registerUser(@RequestBody RegisterUserRequest request) {
        Long userId = userService.registerUser(request.getName());
        return BaseResponse.ok(RegisterUserResponse.builder()
                .userId(userId)
                .build());
    }

}
