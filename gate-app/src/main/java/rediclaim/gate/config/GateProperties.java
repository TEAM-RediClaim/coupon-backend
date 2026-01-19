package rediclaim.gate.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "gate")
public class GateProperties {

    private List<Long> eventIds;

    private String dispatchMode;

    private int dispatchQuantity;

    private String issuerBaseUrl;

    private String kafkaTopic;
}
