package rediclaim.couponbackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rediclaim.couponbackend.domain.Coupon;
import rediclaim.couponbackend.domain.User;
import rediclaim.couponbackend.exception.CustomException;
import rediclaim.couponbackend.repository.CouponRepository;
import rediclaim.couponbackend.repository.UserCouponRepository;
import rediclaim.couponbackend.repository.UserRepository;

import java.util.List;

import static rediclaim.couponbackend.exception.ExceptionResponseStatus.*;

/**
 * [TASK 2] Redis Lua Script 기반 쿠폰 발급 서비스
 *
 * <h3>TASK 1(비관적 락) 대비 개선점</h3>
 * <ul>
 *   <li><b>동시성 제어를 DB → Redis 로 이전</b>: DB Row Lock 을 완전히 제거했다.
 *       Redis 는 Lua 스크립트를 단일 명령으로 처리하므로 별도 락 없이 원자성이 보장된다.</li>
 *   <li><b>Lock 대기 제거</b>: 모든 요청이 순차 대기하는 대신 Redis 에서 마이크로초 단위로
 *       처리된다. HikariCP 커넥션 고갈과 Tomcat 스레드 고갈이 해소된다.</li>
 *   <li><b>스케일 아웃 효과 복원</b>: 앱 서버를 늘려도 Lock 경쟁이 증가하지 않는다.
 *       Redis 는 단일 인스턴스이므로 모든 서버가 같은 재고·발급 상태를 공유한다.</li>
 * </ul>
 *
 * <h3>처리 흐름</h3>
 * <ol>
 *   <li>Redis Lua: 중복 체크 + 재고 체크 + 재고 차감 + 발급 기록 (원자적)</li>
 *   <li>DB 발급 기록: UserCoupon INSERT (단일 쿼리, 엔티티 로드 없음)</li>
 * </ol>
 *
 * <h3>알려진 한계 (TASK 3 에서 해결)</h3>
 * <p>Redis 단계 성공 후 DB 쓰기가 실패하면 두 저장소 사이 불일치가 발생한다.</p>
 */
@Service
@RequiredArgsConstructor
public class CouponIssueServiceV2 {

    private static final String COUPON_STOCK_KEY  = "coupon:stock:%d";
    private static final String COUPON_ISSUED_KEY = "coupon:issued:%d";

    private final StringRedisTemplate       redisTemplate;
    private final DefaultRedisScript<Long>  issueCouponScript;
    private final CouponRepository          couponRepository;
    private final UserCouponRepository      userCouponRepository;
    private final UserRepository            userRepository;

    /**
     * 쿠폰 발급 (선착순 / 중복 금지 / 초과 금지)
     *
     * <p>DB 트랜잭션({@code @Transactional})을 메서드 레벨에 선언하지 않는다.
     * Redis Lua 스크립트 실행 전에 DB 커넥션을 점유하면 재고 소진·중복 발급(400) 케이스에서도
     * 커넥션이 낭비되어 HikariCP 풀 고갈로 이어지기 때문이다.
     * DB 커넥션은 실제 INSERT 가 필요한 경우에만 {@code insertUserCoupon} 내부에서 획득한다.</p>
     */
    public void issueCoupon(Long userId, Long couponId) {

        // ── Step 1. Redis Lua 원자 연산 ─────────────────────────────────────────
        String issuedKey = COUPON_ISSUED_KEY.formatted(couponId);
        String stockKey  = COUPON_STOCK_KEY.formatted(couponId);

        Long result = redisTemplate.execute(
                issueCouponScript,
                List.of(issuedKey, stockKey),
                String.valueOf(userId)
        );

        // ── Step 2. Lua 결과 처리 ────────────────────────────────────────────────
        if (result == null || result == -2L) {
            throw new CustomException(COUPON_NOT_FOUND);
        }
        if (result == -1L) {
            throw new CustomException(USER_ALREADY_HAS_COUPON);
        }
        if (result == 0L) {
            throw new CustomException(COUPON_OUT_OF_STOCK);
        }

        // ── Step 3. DB 발급 기록 ─────────────────────────────────────────────────
        // Redis 가 source of truth 로서 중복·초과 발급을 이미 차단했다.
        // DB 는 발급 이력을 남기는 역할만 하므로, 엔티티 로드 없이 INSERT 쿼리만 실행한다.
        userCouponRepository.insertUserCoupon(userId, couponId);
    }

    /**
     * 쿠폰 생성
     *
     * <p>DB 저장 후 Redis 재고 키를 초기화한다.
     * Redis 초기화가 누락되면 발급 Lua 스크립트가 {@code -2}(쿠폰 없음)를 반환하므로
     * 두 단계는 반드시 함께 수행되어야 한다.</p>
     */
    @Transactional
    public Long createCoupon(Long creatorId, int quantity, String couponName) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
        if (!creator.isCreator()) {
            throw new CustomException(USER_NOT_ALLOWED_TO_CREATE_COUPON);
        }

        Coupon saved = couponRepository.save(Coupon.builder()
                .name(couponName)
                .remainingCount(quantity)
                .creator(creator)
                .build());

        // Redis 재고 초기화 — 이 시점부터 발급 요청을 받을 수 있다.
        String stockKey = COUPON_STOCK_KEY.formatted(saved.getId());
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));

        return saved.getId();
    }
}