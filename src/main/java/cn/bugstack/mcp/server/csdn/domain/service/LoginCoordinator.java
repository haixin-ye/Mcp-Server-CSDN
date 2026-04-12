package cn.bugstack.mcp.server.csdn.domain.service;

import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoginCoordinator {

    private final CSDNSessionProperties sessionProperties;

    public LoginCoordinator(CSDNSessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    public ArticleFunctionResponse buildAuthRequiredResponse(SessionMetadata metadata) {
        String sessionId = metadata.getPendingLoginSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            metadata.setPendingLoginSessionId(sessionId);
        }

        String loginUrl = buildLoginUrl(sessionId);
        metadata.setPendingLoginUrl(loginUrl);

        ArticleFunctionResponse response = new ArticleFunctionResponse();
        response.setStatus("AUTH_REQUIRED");
        response.setMessage("当前未登录或登录态已失效，请先完成 CSDN 登录");
        response.setMsg(response.getMessage());
        response.setLoginUrl(loginUrl);
        response.setRetryable(Boolean.TRUE);
        return response;
    }

    public String buildLoginUrl(String sessionId) {
        return sessionProperties.getPublicBaseUrl() + "/auth/csdn/login?session=" + sessionId;
    }
}
