package cn.bugstack.mcp.server.csdn.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArticleFunctionResponse {

    @JsonProperty("status")
    @JsonPropertyDescription("返回状态：SUCCESS、AUTH_REQUIRED、FAILED")
    private String status;

    @JsonProperty("code")
    @JsonPropertyDescription("兼容旧结构的状态码")
    private Integer code;

    @JsonProperty("message")
    @JsonPropertyDescription("返回说明")
    private String message;

    @JsonProperty("msg")
    @JsonPropertyDescription("兼容旧结构的说明")
    private String msg;

    @JsonProperty("articleUrl")
    @JsonPropertyDescription("文章地址")
    private String articleUrl;

    @JsonProperty("articleId")
    @JsonPropertyDescription("文章ID")
    private Long articleId;

    @JsonProperty("loginUrl")
    @JsonPropertyDescription("未登录时返回的登录链接")
    private String loginUrl;

    @JsonProperty("retryable")
    @JsonPropertyDescription("是否适合重试")
    private Boolean retryable;

}
