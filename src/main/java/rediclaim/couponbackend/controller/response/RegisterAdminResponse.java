package rediclaim.couponbackend.controller.response;

import lombok.Builder;
import lombok.Getter;
import rediclaim.couponbackend.domain.Admin;

@Getter
public class RegisterAdminResponse {

    private Long adminId;

    private Long adminCode;

    @Builder
    private RegisterAdminResponse(Long adminId, Long adminCode) {
        this.adminId = adminId;
        this.adminCode = adminCode;
    }

    public static RegisterAdminResponse of(Admin admin) {
        return RegisterAdminResponse.builder()
                .adminId(admin.getId())
                .adminCode(admin.getCode())
                .build();
    }
}
