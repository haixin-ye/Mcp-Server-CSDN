package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.CSDNPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CSDNPortContractTest {

    @Test
    public void test_icSdnPort_shouldDeclarePublishArticleContract() throws Exception {
        Method method = ICSDNPort.class.getDeclaredMethod("publishArticle", ArticleFunctionRequest.class);

        assertEquals(ArticleFunctionResponse.class, method.getReturnType());
    }

    @Test
    public void test_csdnPort_shouldImplementPublishArticleContract() throws Exception {
        Method method = CSDNPort.class.getDeclaredMethod("publishArticle", ArticleFunctionRequest.class);

        assertEquals(ArticleFunctionResponse.class, method.getReturnType());
    }
}
