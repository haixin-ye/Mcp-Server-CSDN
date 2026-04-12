package cn.bugstack.mcp.server.csdn.domain.service;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import org.springframework.stereotype.Service;
import retrofit2.Response;

@Service
public class SessionManager {

    private static final String LOGIN_KEYWORD = "\u767B\u5F55";

    public SessionState resolveSessionState(SessionMetadata metadata) {
        if (metadata == null || metadata.getState() == null) {
            return SessionState.UNBOUND;
        }

        return metadata.getState();
    }

    public boolean isAuthFailure(Response<ArticleResponseDTO> response) {
        if (response == null) {
            return false;
        }

        int statusCode = response.code();
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }

        ArticleResponseDTO body = response.body();
        if (body == null) {
            return false;
        }

        return containsLoginKeyword(body.getMsg()) || containsLoginKeyword(body.getMessage());
    }

    private boolean containsLoginKeyword(String message) {
        return message != null && message.contains(LOGIN_KEYWORD);
    }
}
