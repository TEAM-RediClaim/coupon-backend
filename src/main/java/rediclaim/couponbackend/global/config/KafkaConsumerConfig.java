package rediclaim.couponbackend.global.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import rediclaim.couponbackend.domain.event.CouponIssueEvent;
import rediclaim.couponbackend.exception.CustomException;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, CouponIssueEvent> consumerFactory,
            KafkaTemplate kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, CouponIssueEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // Dead-letter recoverer : send failing records to <topic>.DLT topic
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) ->
                new TopicPartition(record.topic() + ".DLT", record.partition()));

        // Retry up to 3 times, then publish to DLT
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // CustomException 에 대해서는 재시도 X -> 즉시 DLT로
        errorHandler.addNotRetryableExceptions(
                CustomException.class
        );

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
