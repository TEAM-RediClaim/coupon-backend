package rediclaim.couponbackend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import rediclaim.couponbackend.controller.response.IssuedCoupons;
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
}
