package cn.bugstack.mcp.server.csdn.domain.service;

import cn.bugstack.mcp.server.csdn.domain.adapter.ICSDNPort;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionRequest;
import cn.bugstack.mcp.server.csdn.domain.model.ArticleFunctionResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class CSDNArticleService {

    @Resource
    private ICSDNPort port;

    @Tool(description = "发布文章到CSDN")
    public ArticleFunctionResponse saveArticle(ArticleFunctionRequest request) throws IOException {
        int contentLength = request.getMarkdowncontent() != null ? request.getMarkdowncontent().length() : 0;
        log.info("==================================================");
        log.info("【MCP 接收到大模型指令】准备调用 CSDN 工具");
        log.info(" - 文章标题: {}", request.getTitle());
        log.info(" - 标签: {}", request.getTags());
        log.info(" - 正文长度: {} 个字符", contentLength);
        log.info("==================================================");

        return port.writeArticle(request);
    }
}