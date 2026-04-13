package cn.bugstack.mcp.server.csdn.interfaces.http;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.service.CSDNArticleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/debug")
@ConditionalOnProperty(prefix = "csdn.debug", name = "enabled", havingValue = "true")
public class DebugController {

    private final CSDNArticleService articleService;
    private final ISessionStore sessionStore;

    public DebugController(CSDNArticleService articleService, ISessionStore sessionStore) {
        this.articleService = articleService;
        this.sessionStore = sessionStore;
    }

    @PostMapping(value = "/publish", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ArticleFunctionResponse publish(@RequestBody DebugPublishRequest request) throws Exception {
        ArticleFunctionRequest articleRequest = new ArticleFunctionRequest();
        articleRequest.setTitle(request.getTitle());
        articleRequest.setMarkdowncontent(request.getMarkdowncontent());
        articleRequest.setTags(request.getTags());
        articleRequest.setDescription(request.getDescription());
        return articleService.publishArticle(articleRequest);
    }

    @GetMapping(value = "/session", produces = MediaType.APPLICATION_JSON_VALUE)
    public DebugSessionResponse session() throws IOException {
        SessionMetadata metadata = sessionStore.loadMetadata();
        CSDNAuthState authState = sessionStore.loadAuthState().orElse(null);

        DebugSessionResponse response = new DebugSessionResponse();
        response.setState(metadata.getState() == null ? null : metadata.getState().name());
        response.setPendingLoginSessionId(metadata.getPendingLoginSessionId());
        response.setPendingLoginUrl(metadata.getPendingLoginUrl());
        response.setLastError(metadata.getLastError());
        response.setAuthStatePresent(authState != null);
        response.setCookieLength(authState == null || authState.getCookie() == null ? 0 : authState.getCookie().length());
        return response;
    }
}
