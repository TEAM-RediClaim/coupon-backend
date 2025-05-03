package rediclaim.couponbackend.exception;

public class CustomException extends RuntimeException {

    public CustomException(ExceptionResponseStatus status) {
        super(status.getMessage());
    }

    public CustomException(String message) {
        super(message);
    }
}
