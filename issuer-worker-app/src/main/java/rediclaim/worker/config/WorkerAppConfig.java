package rediclaim.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WorkerProperties.class)
@EnableJpaAuditing
public class WorkerAppConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
