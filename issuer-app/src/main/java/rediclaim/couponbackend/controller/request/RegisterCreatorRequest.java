package rediclaim.couponbackend.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterCreatorRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;
}
