package rediclaim.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {"rediclaim.worker.domain", "rediclaim.couponbackend.domain"})
public class IssuerWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssuerWorkerApplication.class, args);
    }
}