package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.domain.service.LoginCoordinator;
import cn.bugstack.mcp.server.csdn.domain.service.SessionManager;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.CSDNBrowserAuthAdapter;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.CSDNPort;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.ICSDNService;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNApiProperties;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import okhttp3.Request;
import okio.Timeout;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

public class CSDNPortMappingTest {

    @Test
    public void test_publishArticle_shouldMapSuccessFields() throws Exception {
        CSDNPort port = new CSDNPort();
        ReflectionTestUtils.setField(port, "sessionStore", new ActiveSessionStore());
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(new CSDNSessionProperties()));
        ReflectionTestUtils.setField(port, "sessionManager", new SessionManager());
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new SuccessService());
        ReflectionTestUtils.setField(port, "browserAuthAdapter", new StubBrowserAuthAdapter(successResponse(), true));

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(Long.valueOf(1001L), response.getArticleId());
        assertEquals("https://blog.csdn.net/article/details/1001", response.getArticleUrl());
        assertTrue(Boolean.FALSE.equals(response.getRetryable()));
    }

    @Test
    public void test_publishArticle_shouldMapFailedResponse() throws Exception {
        CSDNPort port = new CSDNPort();
        ReflectionTestUtils.setField(port, "sessionStore", new ActiveSessionStore());
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(new CSDNSessionProperties()));
        ReflectionTestUtils.setField(port, "sessionManager", new SessionManager());
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new FailedService());
        ReflectionTestUtils.setField(port, "browserAuthAdapter", new StubBrowserAuthAdapter(new ArticleResponseDTO(), false));

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("FAILED", response.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getRetryable()));
    }

    @Test
    public void test_publishArticle_shouldMapBusinessFailureWhenHttpIs200() throws Exception {
        CSDNPort port = new CSDNPort();
        ReflectionTestUtils.setField(port, "sessionStore", new ActiveSessionStore());
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(new CSDNSessionProperties()));
        ReflectionTestUtils.setField(port, "sessionManager", new SessionManager());
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new BusinessFailedService());
        ReflectionTestUtils.setField(port, "browserAuthAdapter", new StubBrowserAuthAdapter(businessFailedResponse(), true));

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("FAILED", response.getStatus());
        assertEquals(Integer.valueOf(10001), response.getCode());
        assertTrue(Boolean.TRUE.equals(response.getRetryable()));
    }

    @Test
    public void test_publishArticle_shouldMapFailedWhenBodyIsNull() throws Exception {
        CSDNPort port = new CSDNPort();
        ReflectionTestUtils.setField(port, "sessionStore", new ActiveSessionStore());
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(new CSDNSessionProperties()));
        ReflectionTestUtils.setField(port, "sessionManager", new SessionManager());
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new NullBodyService());
        ReflectionTestUtils.setField(port, "browserAuthAdapter", new StubBrowserAuthAdapter(null, true));

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("FAILED", response.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getRetryable()));
    }

    @Test
    public void test_publishArticle_shouldMapFailedWhenCodeIsNull() throws Exception {
        CSDNPort port = new CSDNPort();
        ReflectionTestUtils.setField(port, "sessionStore", new ActiveSessionStore());
        ReflectionTestUtils.setField(port, "loginCoordinator", new LoginCoordinator(new CSDNSessionProperties()));
        ReflectionTestUtils.setField(port, "sessionManager", new SessionManager());
        ReflectionTestUtils.setField(port, "csdnApiProperties", new CSDNApiProperties());
        ReflectionTestUtils.setField(port, "csdnService", new NullCodeService());
        ReflectionTestUtils.setField(port, "browserAuthAdapter", new StubBrowserAuthAdapter(nullCodeResponse(), true));

        ArticleFunctionResponse response = port.publishArticle(new ArticleFunctionRequest());

        assertEquals("FAILED", response.getStatus());
        assertTrue(Boolean.TRUE.equals(response.getRetryable()));
    }

    private static class ActiveSessionStore implements ISessionStore {

        @Override
        public void initialize() {
        }

        @Override
        public SessionMetadata loadMetadata() {
            SessionMetadata metadata = new SessionMetadata();
            metadata.setState(SessionState.ACTIVE);
            return metadata;
        }

        @Override
        public void saveMetadata(SessionMetadata metadata) {
        }

        @Override
        public Optional<CSDNAuthState> loadAuthState() {
            CSDNAuthState authState = new CSDNAuthState();
            authState.setCookie("cookie=value");
            return Optional.of(authState);
        }

        @Override
        public void saveAuthState(CSDNAuthState authState) {
        }

        @Override
        public void clearAuthState() {
        }
    }

    private static ArticleResponseDTO successResponse() {
        ArticleResponseDTO responseDTO = new ArticleResponseDTO();
        responseDTO.setCode(0);
        responseDTO.setMsg("ok");
        ArticleResponseDTO.ArticleData data = new ArticleResponseDTO.ArticleData();
        data.setId(1001L);
        data.setUrl("https://blog.csdn.net/article/details/1001");
        responseDTO.setData(data);
        return responseDTO;
    }

    private static ArticleResponseDTO businessFailedResponse() {
        ArticleResponseDTO responseDTO = new ArticleResponseDTO();
        responseDTO.setCode(10001);
        responseDTO.setMsg("涓氬姟澶辫触");
        return responseDTO;
    }

    private static ArticleResponseDTO nullCodeResponse() {
        ArticleResponseDTO responseDTO = new ArticleResponseDTO();
        responseDTO.setMsg("缂哄皯code");
        return responseDTO;
    }

    private static class SuccessService implements ICSDNService {

        @Override
        public Call<ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            ArticleResponseDTO responseDTO = new ArticleResponseDTO();
            responseDTO.setCode(0);
            responseDTO.setMsg("ok");
            ArticleResponseDTO.ArticleData data = new ArticleResponseDTO.ArticleData();
            data.setId(1001L);
            data.setUrl("https://blog.csdn.net/article/details/1001");
            responseDTO.setData(data);
            return new StaticCall<>(Response.success(responseDTO));
        }
    }

    private static class StubBrowserAuthAdapter extends CSDNBrowserAuthAdapter {

        private final ArticleResponseDTO body;
        private final boolean successful;

        private StubBrowserAuthAdapter(ArticleResponseDTO body, boolean successful) {
            this.body = body;
            this.successful = successful;
        }

        @Override
        public Optional<BrowserPublishResult> publishArticleInBrowser(String cookieHeader, cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO articleRequestDTO) {
            return Optional.of(new BrowserPublishResult(200, successful, body, body == null ? "" : "{}", cookieHeader));
        }
    }

    private static class FailedService implements ICSDNService {

        @Override
        public Call<ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            return new StaticCall<>(Response.error(500, okhttp3.ResponseBody.create(okhttp3.MediaType.get("application/json"), "{}")));
        }
    }

    private static class BusinessFailedService implements ICSDNService {

        @Override
        public Call<ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            ArticleResponseDTO responseDTO = new ArticleResponseDTO();
            responseDTO.setCode(10001);
            responseDTO.setMsg("业务失败");
            return new StaticCall<>(Response.success(responseDTO));
        }
    }

    private static class NullBodyService implements ICSDNService {

        @Override
        public Call<ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            return new StaticCall<>(Response.success(null));
        }
    }

    private static class NullCodeService implements ICSDNService {

        @Override
        public Call<ArticleResponseDTO> saveArticle(
                cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO request,
                String cookieValue,
                String nonce,
                String signature) {
            ArticleResponseDTO responseDTO = new ArticleResponseDTO();
            responseDTO.setMsg("缺少code");
            return new StaticCall<>(Response.success(responseDTO));
        }
    }

    private static class StaticCall<T> implements Call<T> {

        private final Response<T> response;

        private StaticCall(Response<T> response) {
            this.response = response;
        }

        @Override
        public Response<T> execute() {
            return response;
        }

        @Override
        public void enqueue(Callback<T> callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call<T> clone() {
            return new StaticCall<>(response);
        }

        @Override
        public Request request() {
            return new Request.Builder().url("http://localhost/test").build();
        }

        @Override
        public Timeout timeout() {
            return Timeout.NONE;
        }
    }
}
