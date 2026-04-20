package cn.bugstack.mcp.server.csdn.domain.service;

import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.springframework.stereotype.Service;

@Service
public class LoginCoordinator {

    private final CSDNSessionProperties sessionProperties;

    public LoginCoordinator(CSDNSessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    public ArticleFunctionResponse buildAuthRequiredResponse(SessionMetadata metadata) {
        return buildAuthRequiredResponse(metadata, "LOGIN_REQUIRED", "当前未登录或登录态已失效，请先完成 CSDN 登录。");
    }

    public ArticleFunctionResponse buildAuthRequiredResponse(SessionMetadata metadata, String reason, String summary) {
        String sessionId = sessionProperties.getFixedSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }

        String loginPath = buildLoginPath(sessionId);
        String loginUrl = buildLoginUrl(sessionId);

        metadata.setPendingLoginSessionId(sessionId);
        metadata.setPendingLoginUrl(loginUrl);
        metadata.setState(SessionState.LOGIN_PENDING);

        String humanMessage = summary + " 请在浏览器打开以下链接完成登录：" + loginUrl + "。登录成功后，请重新调用 publishArticle。";

        ArticleFunctionResponse response = new ArticleFunctionResponse();
        response.setStatus("AUTH_REQUIRED");
        response.setReason(reason);
        response.setMessage(humanMessage);
        response.setMsg(humanMessage);
        response.setHumanMessage(humanMessage);
        response.setLoginPath(loginPath);
        response.setLoginUrl(loginUrl);
        response.setNextAction("OPEN_LOGIN_URL");
        response.setRetryable(Boolean.TRUE);
        return response;
    }

    public String buildLoginPath(String sessionId) {
        return "/auth/csdn/login?session=" + sessionId;
    }

    public String buildLoginUrl(String sessionId) {
        String baseUrl = sessionProperties.getPublicBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + buildLoginPath(sessionId);
    }
}
