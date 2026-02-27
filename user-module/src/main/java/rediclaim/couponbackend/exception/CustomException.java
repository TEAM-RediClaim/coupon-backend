package rediclaim.couponbackend.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ExceptionResponseStatus status;

    public CustomException(ExceptionResponseStatus status) {
        super(status.getMessage());
        this.status = status;
    }

    public CustomException(ExceptionResponseStatus status, String message) {
        super(message);
        this.status = status;
    }
}