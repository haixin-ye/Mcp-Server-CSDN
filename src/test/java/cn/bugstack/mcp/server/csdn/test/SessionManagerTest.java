package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.domain.service.SessionManager;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionManagerTest {

    @Test
    public void test_resolveSessionState_shouldReturnUnboundWhenMetadataMissing() {
        SessionManager manager = new SessionManager();

        SessionState state = manager.resolveSessionState(null);

        assertEquals(SessionState.UNBOUND, state);
    }

    @Test
    public void test_resolveSessionState_shouldReturnUnboundWhenMetadataStateMissing() {
        SessionManager manager = new SessionManager();

        SessionState state = manager.resolveSessionState(new SessionMetadata());

        assertEquals(SessionState.UNBOUND, state);
    }

    @Test
    public void test_isAuthFailure_shouldReturnTrueForUnauthorizedHttpStatus() {
        SessionManager manager = new SessionManager();
        Response<ArticleResponseDTO> response = Response.error(
                401,
                ResponseBody.create(
                        MediaType.get("application/json"),
                        "{\"msg\":\"unauthorized\"}"
                )
        );

        assertTrue(manager.isAuthFailure(response));
    }

    @Test
    public void test_isAuthFailure_shouldReturnTrueWhenMessageContainsLoginKeyword() {
        SessionManager manager = new SessionManager();
        ArticleResponseDTO body = new ArticleResponseDTO();
        body.setMsg("\u8BF7\u5148\u767B\u5F55\u540E\u518D\u53D1\u5E03");
        Response<ArticleResponseDTO> response = Response.success(body);

        assertTrue(manager.isAuthFailure(response));
    }
}
