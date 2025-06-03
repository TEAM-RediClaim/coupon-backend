package rediclaim.couponbackend.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

@Configuration
@Profile("test")
public class EmbeddedRedisConfig {

    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        // 1) 사용 가능한 포트 번호를 찾아서 port 변수에 할당
        redisPort = findAvailablePort();

        // 2) 찾아낸 포트로 RedisServer 빌드
        this.redisServer = new RedisServer(redisPort);

        // 3) Redis 프로세스 시작
        this.redisServer.start();

        // 4) Spring이 읽도록 system property 또는 환경 변수로 포트 값을 주입
        //    test 환경에서는 application-test.yml 대신 이 값이 적용됨
        System.setProperty("spring.data.redis.port", String.valueOf(redisPort));
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    /**
     * @return JVM이 실행 중인 환경에서 사용 가능한 포트 하나를 찾아서 반환한다.
     * 여러 테스트가 병렬적으로 수행될 경우 address in use error를 막기 위함
     */
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
