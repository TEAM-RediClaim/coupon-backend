package rediclaim.couponbackend.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserType {

    NORMAL("일반 유저"),
    CREATOR("쿠폰 생성자");

    private final String value;
}
