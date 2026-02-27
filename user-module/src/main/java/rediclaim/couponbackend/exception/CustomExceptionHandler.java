package rediclaim.couponbackend.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rediclaim.couponbackend.global.common.BaseResponse;

@RestControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<BaseResponse<Void>> handleCustomException(CustomException e) {
        ExceptionResponseStatus ers = e.getStatus();
        BaseResponse<Void> body = BaseResponse.of(ers.getStatus(), e.getMessage(), null);
        return ResponseEntity.status(ers.getStatus()).body(body);
    }
}