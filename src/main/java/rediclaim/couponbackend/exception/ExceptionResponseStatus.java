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
    COUPON_LOCK_TIMEOUT(1002, HttpStatus.INTERNAL_SERVER_ERROR, "쿠폰 LOCK 획득 대기 시간이 초과되었습니다."),

    /**
     * 1100 : UserCoupon 에러
     */
    USER_ALREADY_HAS_COUPON(1100, HttpStatus.BAD_REQUEST, "이미 발급받은 쿠폰입니다."),

    /**
     * 1200 : User 에러
     */
    USER_NOT_FOUND(1200, HttpStatus.NOT_FOUND, "존재하지 않는 유저입니다."),
    USER_NOT_ALLOWED_TO_CREATE_COUPON(1201, HttpStatus.BAD_REQUEST, "일반 유저는 쿠폰을 생성할 수 없습니다."),

    /**
     * 4000 : Request Validation 에러
     */
    REQUEST_VALIDATION_FAILED(4000, HttpStatus.BAD_REQUEST, "요청 데이터 검증에 실패했습니다."),

    /**
     * 5000 : Database 에러
     */
    DATABASE_ERROR(5000, HttpStatus.INTERNAL_SERVER_ERROR, "database에서 error가 발생했습니다")
    ;

    private final int code;

    private final HttpStatus status;

    private final String message;
}
