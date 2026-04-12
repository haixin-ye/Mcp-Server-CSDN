package cn.bugstack.mcp.server.csdn.types.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "csdn.session")
public class CSDNSessionProperties {

    private String dataRoot = "/app/data";
    private Integer loginTimeoutMinutes = 10;
    private String publicBaseUrl = "http://127.0.0.1:18080";

}
