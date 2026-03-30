package rediclaim.issuer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan(basePackages = {"rediclaim.issuer.domain", "rediclaim.couponbackend.domain"})
public class IssuerApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssuerApiApplication.class, args);
    }
}