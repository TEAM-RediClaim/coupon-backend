package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.repository.UserRepository;

import static rediclaim.couponbackend.domain.UserType.CREATOR;
import static rediclaim.couponbackend.domain.UserType.NORMAL;

/**
 * 유저 등록 서비스 (공유 모듈)
 *
 * <p>issuer-app, issuer-app-task2 에서 공통으로 사용하는 유저 등록 로직만 포함한다.
 * 발급된 쿠폰 조회({@code showAllIssuedCoupons})는 Coupon·UserCoupon 도메인에 의존하므로
 * 각 issuer 모듈의 {@code UserCouponService} 에 위치한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public Long registerUser(String name) {
        return userRepository.save(User.builder()
                .name(name)
                .userType(NORMAL)
                .build()).getId();
    }

    @Transactional
    public Long registerCreator(String name) {
        return userRepository.save(User.builder()
                .name(name)
                .userType(CREATOR)
                .build()).getId();
    }
}