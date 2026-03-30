package rediclaim.worker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    private String kafkaTopic;
    private String consumerGroup;
    private String gateBaseUrl;
}