package rediclaim.couponbackend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisConfig {

    /**
     * 모든 키·값을 String 으로 직렬화하는 RedisTemplate.
     * Lua 스크립트의 KEYS / ARGV 가 모두 String 이므로 StringRedisTemplate 을 그대로 사용한다.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 쿠폰 발급 Lua 스크립트 빈.
     * ClassPathResource 로 로드하면 스크립트 본문을 Java 코드에서 분리할 수 있다.
     *
     * <p>반환 타입은 Long:
     * <ul>
     *   <li> 1 : 발급 성공</li>
     *   <li> 0 : 재고 없음</li>
     *   <li>-1 : 중복 발급</li>
     *   <li>-2 : 쿠폰 없음 (Redis 미초기화)</li>
     * </ul>
     */
    @Bean
    public DefaultRedisScript<Long> issueCouponScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/issue-coupon.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
