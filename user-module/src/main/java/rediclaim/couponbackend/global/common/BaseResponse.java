package rediclaim.couponbackend.global.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BaseResponse<T> {

    private final int code;
    private final HttpStatus status;
    private final String message;
    private final T result;

    public BaseResponse(HttpStatus status, String message, T result) {
        this.code = status.value();
        this.status = status;
        this.message = message;
        this.result = result;
    }

    public static <T> BaseResponse<T> of(HttpStatus httpStatus, String message, T result) {
        return new BaseResponse<>(httpStatus, message, result);
    }

    public static <T> BaseResponse<T> of(HttpStatus httpStatus, T result) {
        return of(httpStatus, httpStatus.name(), result);
    }

    public static <T> BaseResponse<T> ok(T result) {
        return of(HttpStatus.OK, result);
    }
}