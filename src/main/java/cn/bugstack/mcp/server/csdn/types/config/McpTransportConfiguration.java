package cn.bugstack.mcp.server.csdn.types.config;

import cn.bugstack.mcp.server.csdn.infrastructure.transport.SafeWebMvcSseServerTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.autoconfigure.mcp.server.McpServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class McpTransportConfiguration {

    @Bean
    public SafeWebMvcSseServerTransport safeWebMvcSseServerTransport(
            ObjectMapper objectMapper,
            McpServerProperties serverProperties
    ) {
        return new SafeWebMvcSseServerTransport(objectMapper, serverProperties.getSseMessageEndpoint());
    }

    @Bean
    public RouterFunction<ServerResponse> mvcMcpRouterFunction(SafeWebMvcSseServerTransport transport) {
        return transport.getRouterFunction();
    }
}
