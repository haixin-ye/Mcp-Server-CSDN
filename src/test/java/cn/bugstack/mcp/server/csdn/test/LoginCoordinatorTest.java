package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.domain.service.LoginCoordinator;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.CSDNPort;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.ICSDNService;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNApiProperties;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginCoordinatorTest {

    @Test
    public void test_buildAuthRequiredResponse_shouldUseConfiguredLoginUrl() {
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setPublicBaseUrl("https://mcp.example.com");
        LoginCoordinator coordinator = new LoginCoordinator(properties);
        SessionMetadata metadata = new SessionMetadata();
        metadata.setState(SessionState.UNBOUND);
        metadata.setPendingLoginSessionId("session-123");

        ArticleFunctionResponse response = coordinator.buildAuthRequiredResponse(metadata);

        assertEquals("AUTH_REQUIRED", response.getStatus());
        assertNotNull(response.getMessage());
        assertTrue(response.getMessage().contains("登录"));
        assertEquals(response.getMessage(), response.getMsg());
        assertEquals("https://mcp.example.com/auth/csdn/login?session=session-123", response.getLoginUrl());
        assertTrue(Boolean.TRUE.equals(response.getRetryable()));
        assertNull(response.getArticleId());
        assertNull(response.getArticleUrl());
    }

    @Test
    public void test_buildAuthRequiredResponse_shouldGenerateSessionIdWhenMissing() {
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setPublicBaseUrl("https://mcp.example.com");
        LoginCoordinator coordinator = new LoginCoordinator(properties);
        SessionMetadata metadata = new SessionMetadata();
        metadata.setState(SessionState.UNBOUND);

        ArticleFunctionResponse response = coordinator.buildAuthRequiredResponse(metadata);

        assertNotNull(metadata.getPendingLoginSessionId());
        assertNotNull(metadata.getPendingLoginUrl());
        assertTrue(response.getLoginUrl().startsWith("https://mcp.example.com/auth/csdn/login?session="));
    }

    @Test
    public void test_publishArticle_shouldReturnAuthRequiredWhenSessionUnbound() throws Exception {
        CSDNPort port = new CSDNPort();
        StubSessionStore sessionStore = new StubSessionStore(buildMetadata(SessionState.UNBOUND, "session-unbound"));
        ReflectionTestUtils.setField(port, "sessionStore", sessionStore);
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(buildSessionProperties()));
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new FailingCsdnService());

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("AUTH_REQUIRED", response.getStatus());
        assertEquals("http://127.0.0.1:18080/auth/csdn/login?session=session-unbound", response.getLoginUrl());
        assertTrue(sessionStore.saveCalled);
        assertSame(sessionStore.metadata, sessionStore.savedMetadata);
    }

    @Test
    public void test_publishArticle_shouldReturnAuthRequiredWhenSessionExpired() throws Exception {
        CSDNPort port = new CSDNPort();
        StubSessionStore sessionStore = new StubSessionStore(buildMetadata(SessionState.EXPIRED, "session-expired"));
        ReflectionTestUtils.setField(port, "sessionStore", sessionStore);
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(buildSessionProperties()));
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new FailingCsdnService());

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("AUTH_REQUIRED", response.getStatus());
        assertEquals("http://127.0.0.1:18080/auth/csdn/login?session=session-expired", response.getLoginUrl());
        assertTrue(sessionStore.saveCalled);
        assertSame(sessionStore.metadata, sessionStore.savedMetadata);
    }

    private static SessionMetadata buildMetadata(SessionState state, String sessionId) {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setState(state);
        metadata.setPendingLoginSessionId(sessionId);
        return metadata;
    }

    private static CSDNSessionProperties buildSessionProperties() {
        return new CSDNSessionProperties();
    }

    private static class StubSessionStore implements ISessionStore {

        private final SessionMetadata metadata;
        private boolean saveCalled;
        private SessionMetadata savedMetadata;

        private StubSessionStore(SessionMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public void initialize() {
        }

        @Override
        public SessionMetadata loadMetadata() {
            return metadata;
        }

        @Override
        public void saveMetadata(SessionMetadata metadata) {
            this.saveCalled = true;
            this.savedMetadata = metadata;
        }
    }

    private static class FailingCsdnService implements ICSDNService {

        @Override
        public retrofit2.Call<cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            throw new IllegalStateException("remote API should not be called");
        }
    }
}
