package cn.bugstack.mcp.server.csdn.domain.model;

import cn.bugstack.mcp.server.csdn.types.utils.MarkdownConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArticleFunctionRequest {

    @JsonProperty(required = true, value = "title")
    @JsonPropertyDescription("文章标题")
    private String title;

    @JsonProperty(required = true, value = "markdowncontent")
    @JsonPropertyDescription("Markdown 正文内容")
    private String markdowncontent;

    @JsonProperty(required = true, value = "tags")
    @JsonPropertyDescription("文章标签，使用英文逗号分隔")
    private String tags;

    @JsonProperty(required = true, value = "description")
    @JsonPropertyDescription("文章摘要")
    private String description;

    public String getContent() {
        return MarkdownConverter.convertToHtml(markdowncontent);
    }
}
