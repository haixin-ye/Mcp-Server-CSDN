package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.domain.service.CSDNArticleService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CSDNArticleServiceResponseTest {

    @Test
    public void test_articleFunctionResponse_shouldExposeUnifiedJsonProperties() throws Exception {
        Map<String, String> expectedProperties = new LinkedHashMap<>();
        expectedProperties.put("status", "status");
        expectedProperties.put("message", "message");
        expectedProperties.put("articleUrl", "articleUrl");
        expectedProperties.put("articleId", "articleId");
        expectedProperties.put("loginUrl", "loginUrl");
        expectedProperties.put("retryable", "retryable");

        for (Map.Entry<String, String> entry : expectedProperties.entrySet()) {
            Field field = ArticleFunctionResponse.class.getDeclaredField(entry.getKey());
            JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);

            assertNotNull(jsonProperty, "missing @JsonProperty on " + entry.getKey());
            assertEquals(entry.getValue(), jsonProperty.value(), "unexpected json name for " + entry.getKey());
        }
    }

    @Test
    public void test_publishArticle_shouldExposeToolDescriptionForLoginLinkFlow() throws Exception {
        Method method = CSDNArticleService.class.getDeclaredMethod("publishArticle", ArticleFunctionRequest.class);
        Tool tool = method.getAnnotation(Tool.class);

        assertNotNull(tool);
        assertTrue(tool.description().contains("未登录时返回登录链接"));
    }

    @Test
    public void test_publishArticle_shouldDelegateRequestToPort() throws Exception {
        CSDNArticleService service = new CSDNArticleService();
        RecordingCsdnPort port = new RecordingCsdnPort();
        injectPort(service, port);

        ArticleFunctionRequest request = new ArticleFunctionRequest();
        request.setTitle("测试标题");
        request.setMarkdowncontent("# 标题");
        request.setTags("csdn,test");
        request.setDescription("测试摘要");

        ArticleFunctionResponse response = service.publishArticle(request);

        assertSame(request, port.invokedRequest);
        assertSame(port.responseToReturn, response);
    }

    private static void injectPort(CSDNArticleService service, ICSDNPort port) throws Exception {
        Field field = CSDNArticleService.class.getDeclaredField("port");
        field.setAccessible(true);
        field.set(service, port);
    }

    private static class RecordingCsdnPort implements ICSDNPort {

        private ArticleFunctionRequest invokedRequest;
        private final ArticleFunctionResponse responseToReturn = new ArticleFunctionResponse();

        @Override
        public ArticleFunctionResponse writeArticle(ArticleFunctionRequest request) throws IOException {
            this.invokedRequest = request;
            responseToReturn.setStatus("SUCCESS");
            responseToReturn.setMessage("ok");
            return responseToReturn;
        }
    }
}
