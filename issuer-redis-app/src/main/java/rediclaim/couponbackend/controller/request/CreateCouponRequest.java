package rediclaim.couponbackend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCouponRequest {

    @NotNull(message = "쿠폰 생성자 id값은 필수입니다.")
    private Long creatorId;

    @NotBlank(message = "생성할 쿠폰 이름은 필수입니다.")
    private String couponName;

    @NotNull(message = "생성할 쿠폰 수량은 필수입니다.")
    private int quantity;
}