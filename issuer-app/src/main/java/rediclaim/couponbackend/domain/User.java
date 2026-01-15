package rediclaim.couponbackend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static rediclaim.couponbackend.domain.UserType.CREATOR;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserType userType;

    @Builder
    private User(String name, UserType userType) {
        this.name = name;
        this.userType = userType;
    }

    public boolean isSameUser(Long userId) {
        return id.equals(userId);
    }

    public boolean isCreator() {
        return userType == CREATOR;
    }
}
