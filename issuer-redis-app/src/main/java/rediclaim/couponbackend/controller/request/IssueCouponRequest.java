package rediclaim.couponbackend.controller.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueCouponRequest {

    @NotNull(message = "유저 id 값은 필수입니다.")
    private Long userId;
}