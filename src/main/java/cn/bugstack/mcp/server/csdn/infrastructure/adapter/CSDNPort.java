package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
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

    @Override
    public ArticleFunctionResponse publishArticle(ArticleFunctionRequest request) throws IOException {
        SessionMetadata metadata = sessionStore.loadMetadata();
        if (metadata == null) {
            metadata = SessionMetadata.unbound();
        }
        SessionState sessionState = sessionManager.resolveSessionState(metadata);
        metadata.setState(sessionState);
        if (sessionState == SessionState.UNBOUND || sessionState == SessionState.EXPIRED) {
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        ArticleRequestDTO articleRequestDTO = new ArticleRequestDTO();
        articleRequestDTO.setTitle(request.getTitle());
        articleRequestDTO.setMarkdowncontent(request.getMarkdowncontent());
        articleRequestDTO.setContent(request.getContent());
        articleRequestDTO.setTags(request.getTags());
        articleRequestDTO.setDescription(request.getDescription());
        articleRequestDTO.setCategories(csdnApiProperties.getCategories());

        Call<ArticleResponseDTO> call = csdnService.saveArticle(articleRequestDTO, csdnApiProperties.getCookie(),csdnApiProperties.getNonce(),csdnApiProperties.getSignature());
        Response<ArticleResponseDTO> response = call.execute();

        log.info("请求CSDN发帖 \nreq:{} \nres:{}", JSON.toJSONString(articleRequestDTO), JSON.toJSONString(response));

        if (sessionManager.isAuthFailure(response)) {
            metadata.setState(SessionState.EXPIRED);
            metadata.setLastError("CSDN 发布鉴权失败，需要重新登录");
            ArticleFunctionResponse authRequired = loginCoordinator.buildAuthRequiredResponse(metadata);
            sessionStore.saveMetadata(metadata);
            return authRequired;
        }

        if (response.isSuccessful()) {
            ArticleResponseDTO articleResponseDTO = response.body();
            if (null == articleResponseDTO) return null;

            ArticleFunctionResponse articleFunctionResponse = new ArticleFunctionResponse();
            articleFunctionResponse.setCode(articleResponseDTO.getCode());
            articleFunctionResponse.setMsg(articleResponseDTO.getMsg());

            return articleFunctionResponse;
        }

        return null;
    }

}
