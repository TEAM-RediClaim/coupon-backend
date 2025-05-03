package rediclaim.couponbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class CouponBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CouponBackendApplication.class, args);
    }

}
