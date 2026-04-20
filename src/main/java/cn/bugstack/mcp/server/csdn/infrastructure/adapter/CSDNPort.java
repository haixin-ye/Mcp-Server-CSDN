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

    private static final String LOGIN_REQUIRED_REASON = "LOGIN_REQUIRED";
    private static final String SESSION_EXPIRED_REASON = "SESSION_EXPIRED";
    private static final String PUBLISHED_REASON = "PUBLISHED";
    private static final String PUBLISH_FAILED_REASON = "PUBLISH_FAILED";
    private static final String RATE_LIMITED_REASON = "RATE_LIMITED";
    private static final String CONTENT_WRITE_FAILED_REASON = "CONTENT_WRITE_FAILED";
    private static final String EMPTY_RESPONSE_REASON = "EMPTY_RESPONSE";

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
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(
                    metadata,
                    LOGIN_REQUIRED_REASON,
                    "当前未登录或登录态已失效。"
            );
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
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(
                    metadata,
                    LOGIN_REQUIRED_REASON,
                    "当前未检测到可用登录态。"
            );
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
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(
                    metadata,
                    SESSION_EXPIRED_REASON,
                    "当前登录态不可用。"
            );
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
        log.info("request CSDN publish (retrofit), req:{}, res:{}",
                JSON.toJSONString(articleRequestDTO),
                JSON.toJSONString(response));

        if (sessionManager.isAuthFailure(response)) {
            metadata.setState(SessionState.EXPIRED);
            metadata.setLastError("CSDN 登录态已失效，请重新登录。");
            sessionStore.clearAuthState();
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(
                    metadata,
                    SESSION_EXPIRED_REASON,
                    "CSDN 登录态已失效。"
            );
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (!response.isSuccessful()) {
            return buildFailedResponse(PUBLISH_FAILED_REASON, "CSDN 发帖失败，请稍后重试。", "RETRY", true);
        }

        return mapResponseBody(response.body(), metadata);
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
            metadata.setLastError("CSDN 登录态已失效，请重新登录。");
            sessionStore.clearAuthState();
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(
                    metadata,
                    SESSION_EXPIRED_REASON,
                    "CSDN 登录态已失效。"
            );
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (!browserResult.isSuccessful()) {
            if (browserResult.getBody() != null) {
                return mapResponseBody(browserResult.getBody(), metadata);
            }
            return buildFailedResponse(PUBLISH_FAILED_REASON, "CSDN 发帖失败，请稍后重试。", "RETRY", true);
        }

        return mapResponseBody(browserResult.getBody(), metadata);
    }

    private ArticleFunctionResponse mapResponseBody(ArticleResponseDTO articleResponseDTO, SessionMetadata metadata) throws IOException {
        if (articleResponseDTO == null) {
            return buildFailedResponse(EMPTY_RESPONSE_REASON, "CSDN 返回空响应体。", "RETRY", true);
        }

        if (isPublishSuccess(articleResponseDTO)) {
            ArticleFunctionResponse success = new ArticleFunctionResponse();
            success.setStatus("SUCCESS");
            success.setReason(PUBLISHED_REASON);
            success.setCode(articleResponseDTO.getCode());
            success.setMessage("CSDN 发帖成功。");
            success.setMsg(success.getMessage());
            success.setHumanMessage(articleResponseDTO.getData() != null && articleResponseDTO.getData().getUrl() != null
                    ? "CSDN 发帖成功，文章地址：" + articleResponseDTO.getData().getUrl()
                    : "CSDN 发帖成功。");
            success.setNextAction("NONE");
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

        String upstreamMessage = firstNonBlank(articleResponseDTO.getMsg(), articleResponseDTO.getMessage());
        if (isRateLimited(articleResponseDTO)) {
            ArticleFunctionResponse failed = buildFailedResponse(
                    RATE_LIMITED_REASON,
                    upstreamMessage == null ? "CSDN 限制了短时间重复发帖，请稍后再试。" : upstreamMessage,
                    "WAIT_AND_RETRY",
                    true
            );
            failed.setCode(articleResponseDTO.getCode());
            return failed;
        }

        if (isContentWriteFailure(articleResponseDTO)) {
            ArticleFunctionResponse failed = buildFailedResponse(
                    CONTENT_WRITE_FAILED_REASON,
                    upstreamMessage == null ? "编辑器正文写入失败，请重试。" : upstreamMessage,
                    "RETRY",
                    true
            );
            failed.setCode(articleResponseDTO.getCode());
            return failed;
        }

        ArticleFunctionResponse failed = buildFailedResponse(
                PUBLISH_FAILED_REASON,
                upstreamMessage == null ? "CSDN 发帖失败，请稍后重试。" : upstreamMessage,
                "RETRY",
                true
        );
        failed.setCode(articleResponseDTO.getCode());
        return failed;
    }

    private boolean isPublishSuccess(ArticleResponseDTO articleResponseDTO) {
        if (articleResponseDTO == null) {
            return false;
        }
        Integer code = articleResponseDTO.getCode();
        String message = firstNonBlank(articleResponseDTO.getMsg(), articleResponseDTO.getMessage());
        return Integer.valueOf(0).equals(code)
                || Integer.valueOf(200).equals(code)
                || "success".equalsIgnoreCase(message);
    }

    private boolean isRateLimited(ArticleResponseDTO articleResponseDTO) {
        if (articleResponseDTO == null) {
            return false;
        }
        String message = firstNonBlank(articleResponseDTO.getMsg(), articleResponseDTO.getMessage());
        return message != null && (message.contains("频繁") || message.contains("稍后再试"));
    }

    private boolean isContentWriteFailure(ArticleResponseDTO articleResponseDTO) {
        if (articleResponseDTO == null) {
            return false;
        }
        String message = firstNonBlank(articleResponseDTO.getMsg(), articleResponseDTO.getMessage());
        return articleResponseDTO.getCode() != null
                && articleResponseDTO.getCode() == 422
                && message != null
                && message.contains("正文写入失败");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private boolean isAuthFailure(int statusCode, ArticleResponseDTO body) {
        if (statusCode == 401 || statusCode == 403) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String message = firstNonBlank(body.getMsg(), body.getMessage());
        return message != null && message.contains("登录");
    }

    private ArticleFunctionResponse buildFailedResponse(String reason, String message, String nextAction, boolean retryable) {
        ArticleFunctionResponse failed = new ArticleFunctionResponse();
        failed.setStatus("FAILED");
        failed.setReason(reason);
        failed.setMessage(message);
        failed.setMsg(message);
        failed.setHumanMessage(message);
        failed.setNextAction(nextAction);
        failed.setRetryable(retryable);
        return failed;
    }
}
