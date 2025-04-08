package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rediclaim.couponbackend.controller.request.RegisterAdminRequest;
import rediclaim.couponbackend.controller.request.RegisterUserRequest;
import rediclaim.couponbackend.controller.response.IssuedCouponsResponse;
import rediclaim.couponbackend.controller.response.RegisterAdminResponse;
import rediclaim.couponbackend.controller.response.RegisterUserResponse;
import rediclaim.couponbackend.global.common.BaseResponse;
import rediclaim.couponbackend.service.UserService;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users/{userId}/coupons")
    public BaseResponse<IssuedCouponsResponse> showAllIssuedCoupons(@PathVariable Long userId) {
        return BaseResponse.ok(userService.showAllIssuedCoupons(userId));
    }

    @PostMapping("/api/users")
    public BaseResponse<RegisterUserResponse> registerUser(@RequestBody RegisterUserRequest request) {
        Long userId = userService.registerUser(request.getName());
        return BaseResponse.ok(RegisterUserResponse.builder()
                .userId(userId)
                .build());
    }

    @PostMapping("/api/admins")
    public BaseResponse<RegisterAdminResponse> registerAdmin(@RequestBody RegisterAdminRequest request) {
        return BaseResponse.ok(userService.registerAdmin(request.getName()));
    }

}
