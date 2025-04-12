package rediclaim.couponbackend.exception;

public class BadRequestException extends RuntimeException {

    public BadRequestException(ExceptionResponseStatus status) {
        super(status.getMessage());
    }

    public BadRequestException(String message) {
        super(message);
    }
}
