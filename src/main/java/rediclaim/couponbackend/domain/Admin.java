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

    public static Admin createNew(String name) {
        Long code = createUniqueCode();
        return Admin.builder()
                .name(name)
                .code(code)
                .build();
    }

    /**
     * 해당 메서드 호출되는 시각(밀리초 단위)으로 adminCode 부여
     * -> 트래픽 많아질 경우 중복된 code 생성될 수 있음
     */
    private static Long createUniqueCode() {
        return System.currentTimeMillis();
    }

    public boolean isValidAdminCode(Long code) {
        return this.code.equals(code);
    }
}
