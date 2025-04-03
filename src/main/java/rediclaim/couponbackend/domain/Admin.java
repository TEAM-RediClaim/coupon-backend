package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admins")
public class Admin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Long code;

    @Builder
    private Admin(String name, Long code) {
        this.name = name;
        this.code = code;
    }

    public boolean isValidAdminCode(Long code) {
        return this.code.equals(code);
    }
}
