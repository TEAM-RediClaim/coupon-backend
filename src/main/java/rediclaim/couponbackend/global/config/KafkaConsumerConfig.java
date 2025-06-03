package rediclaim.couponbackend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import rediclaim.couponbackend.domain.event.CouponIssueEvent;
import rediclaim.couponbackend.exception.CustomException;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, CouponIssueEvent> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 1초 간격으로 최대 3회 재시도, 이후에는 스킵 (DLT로 메시지 이동)
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(backOff);

        // CustomException 에 대해서는 재시도 X
        errorHandler.addNotRetryableExceptions(
                CustomException.class
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
