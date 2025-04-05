package rediclaim.couponbackend.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ExceptionResponseStatus {

    /**
     * 1000 : Coupon 에러
     */
    COUPON_NOT_FOUND(1000, HttpStatus.NOT_FOUND, "존재하지 않는 쿠폰입니다."),
    COUPON_OUT_OF_STOCK(1001, HttpStatus.BAD_REQUEST, "쿠폰 재고가 부족합니다."),

    /**
     * 1100 : UserCoupon 에러
     */
    USER_ALREADY_HAS_COUPON(1100, HttpStatus.BAD_REQUEST, "이미 발급받은 쿠폰입니다."),

    /**
     * 1200 : User 에러
     */
    USER_NOT_FOUND(1200, HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),

    /**
     * 1300 : Admin 에러
     */
    ADMIN_NOT_FOUND(1300, HttpStatus.NOT_FOUND, "존재하지 않는 관리자입니다."),
    INVALID_ADMIN_CODE(1301, HttpStatus.BAD_REQUEST, "유효하지 않은 관리자 코드입니다.")

    ;

    private final int code;

    private final HttpStatus status;

    private final String message;
}
