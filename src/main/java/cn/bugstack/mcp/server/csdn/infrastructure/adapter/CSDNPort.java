package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.domain.service.LoginCoordinator;
import cn.bugstack.mcp.server.csdn.domain.service.SessionManager;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.ICSDNService;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNApiProperties;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class CSDNPort implements ICSDNPort {

    @Resource
    private ICSDNService csdnService;

    @Resource
    private CSDNApiProperties csdnApiProperties;

    @Resource
    private ISessionStore sessionStore;

    @Resource
    private LoginCoordinator loginCoordinator;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private CSDNBrowserAuthAdapter browserAuthAdapter;

    @Override
    public ArticleFunctionResponse publishArticle(ArticleFunctionRequest request) throws IOException {
        SessionMetadata metadata = sessionStore.loadMetadata();
        if (metadata == null) {
            metadata = SessionMetadata.unbound();
        }

        SessionState sessionState = sessionManager.resolveSessionState(metadata);
        metadata.setState(sessionState);
        if (sessionManager.shouldRequireLogin(sessionState)) {
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        CSDNAuthState authState = sessionStore.loadAuthState().orElse(null);
        log.info("publish precheck, state={}, authStatePresent={}, cookieLength={}",
                metadata.getState(),
                authState != null,
                authState == null || authState.getCookie() == null ? 0 : authState.getCookie().length());
        if (!sessionManager.isAuthStateUsable(authState)) {
            metadata.setState(SessionState.UNBOUND);
            metadata.setLastError("未检测到可用登录态，请重新登录。");
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (sessionState == SessionState.LOGIN_PENDING) {
            metadata.setState(SessionState.ACTIVE);
            metadata.setLastError(null);
            sessionStore.saveMetadata(metadata);
            log.info("promote session state from LOGIN_PENDING to ACTIVE because auth-state is available");
        }

        ArticleRequestDTO articleRequestDTO = buildArticleRequest(request);

        CSDNBrowserAuthAdapter.BrowserPublishResult browserResult = browserAuthAdapter
                .publishArticleInBrowser(authState.getCookie(), articleRequestDTO)
                .orElse(null);
        if (browserResult != null) {
            return mapBrowserResult(browserResult, metadata, authState, articleRequestDTO);
        }

        CSDNBrowserAuthAdapter.CSDNDynamicHeaders dynamicHeaders = browserAuthAdapter
                .resolveDynamicHeaders(authState.getCookie())
                .orElse(null);
        if (dynamicHeaders == null) {
            metadata.setState(SessionState.EXPIRED);
            metadata.setLastError("浏览器内发帖未完成，请重新登录后重试。");
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        authState.setCookie(dynamicHeaders.getCookie());
        authState.setNonce(dynamicHeaders.getNonce());
        authState.setSignature(dynamicHeaders.getSignature());
        sessionStore.saveAuthState(authState);

        Call<ArticleResponseDTO> call = csdnService.saveArticle(
                articleRequestDTO,
                dynamicHeaders.getCookie(),
                dynamicHeaders.getNonce(),
                dynamicHeaders.getSignature()
        );
        Response<ArticleResponseDTO> response = call.execute();
        log.info("request CSDN publish (retrofit), req:{}, res:{}", JSON.toJSONString(articleRequestDTO), JSON.toJSONString(response));

        if (sessionManager.isAuthFailure(response)) {
            metadata.setState(SessionState.EXPIRED);
            metadata.setLastError("CSDN 鉴权失败，请重新登录。");
            sessionStore.clearAuthState();
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (!response.isSuccessful()) {
            return buildFailedResponse("CSDN 发帖失败", true);
        }

        ArticleResponseDTO articleResponseDTO = response.body();
        return mapResponseBody(articleResponseDTO, metadata);
    }

    private ArticleRequestDTO buildArticleRequest(ArticleFunctionRequest request) {
        ArticleRequestDTO articleRequestDTO = new ArticleRequestDTO();
        articleRequestDTO.setTitle(request.getTitle());
        articleRequestDTO.setMarkdowncontent(request.getMarkdowncontent());
        articleRequestDTO.setContent(request.getContent());
        articleRequestDTO.setTags(request.getTags());
        articleRequestDTO.setDescription(request.getDescription());
        articleRequestDTO.setCategories(csdnApiProperties.getCategories());
        return articleRequestDTO;
    }

    private ArticleFunctionResponse mapBrowserResult(
            CSDNBrowserAuthAdapter.BrowserPublishResult browserResult,
            SessionMetadata metadata,
            CSDNAuthState authState,
            ArticleRequestDTO articleRequestDTO
    ) throws IOException {
        authState.setCookie(browserResult.getCookie());
        sessionStore.saveAuthState(authState);

        log.info("request CSDN publish (browser), req:{}, status:{}, body:{}",
                JSON.toJSONString(articleRequestDTO),
                browserResult.getStatusCode(),
                browserResult.getRawBody());

        if (isAuthFailure(browserResult.getStatusCode(), browserResult.getBody())) {
            metadata.setState(SessionState.EXPIRED);
            metadata.setLastError("CSDN 鉴权失败，请重新登录。");
            sessionStore.clearAuthState();
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (!browserResult.isSuccessful()) {
            return buildFailedResponse("CSDN 发帖失败", true);
        }

        return mapResponseBody(browserResult.getBody(), metadata);
    }

    private ArticleFunctionResponse mapResponseBody(ArticleResponseDTO articleResponseDTO, SessionMetadata metadata) throws IOException {
        if (articleResponseDTO == null) {
            return buildFailedResponse("CSDN 返回空响应体", true);
        }

        if (articleResponseDTO.getCode() == null || articleResponseDTO.getCode() != 0) {
            ArticleFunctionResponse failed = buildFailedResponse(
                    articleResponseDTO.getMsg() == null ? "CSDN 业务响应缺少成功状态码" : articleResponseDTO.getMsg(),
                    true
            );
            failed.setCode(articleResponseDTO.getCode());
            failed.setMsg(failed.getMessage());
            return failed;
        }

        ArticleFunctionResponse success = new ArticleFunctionResponse();
        success.setStatus("SUCCESS");
        success.setCode(articleResponseDTO.getCode());
        success.setMessage(articleResponseDTO.getMsg());
        success.setMsg(articleResponseDTO.getMsg());
        success.setRetryable(Boolean.FALSE);
        if (articleResponseDTO.getData() != null) {
            success.setArticleId(articleResponseDTO.getData().getId());
            success.setArticleUrl(articleResponseDTO.getData().getUrl());
        }

        metadata.setState(SessionState.ACTIVE);
        metadata.setLastValidatedAt(LocalDateTime.now());
        metadata.setLastError(null);
        sessionStore.saveMetadata(metadata);
        return success;
    }

    private boolean isAuthFailure(int statusCode, ArticleResponseDTO body) {
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String message = body.getMsg();
        if (message == null || message.isBlank()) {
            message = body.getMessage();
        }
        return message != null && message.contains("登录");
    }

    private ArticleFunctionResponse buildFailedResponse(String message, boolean retryable) {
        ArticleFunctionResponse failed = new ArticleFunctionResponse();
        failed.setStatus("FAILED");
        failed.setMessage(message);
        failed.setRetryable(retryable);
        return failed;
    }
}
