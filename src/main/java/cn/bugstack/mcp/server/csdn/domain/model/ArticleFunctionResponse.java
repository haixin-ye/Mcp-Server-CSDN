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

    @JsonProperty("reason")
    @JsonPropertyDescription("更细粒度的原因，例如 LOGIN_REQUIRED、SESSION_EXPIRED、PUBLISHED、RATE_LIMITED、PUBLISH_FAILED")
    private String reason;

    @JsonProperty("code")
    @JsonPropertyDescription("上游业务状态码")
    private Integer code;

    @JsonProperty("message")
    @JsonPropertyDescription("面向调用方的主要返回说明")
    private String message;

    @JsonProperty("msg")
    @JsonPropertyDescription("兼容旧结构的说明字段")
    private String msg;

    @JsonProperty("humanMessage")
    @JsonPropertyDescription("面向最终用户展示的完整提示信息")
    private String humanMessage;

    @JsonProperty("articleUrl")
    @JsonPropertyDescription("文章地址")
    private String articleUrl;

    @JsonProperty("articleId")
    @JsonPropertyDescription("文章 ID")
    private Long articleId;

    @JsonProperty("loginPath")
    @JsonPropertyDescription("相对登录路径，供客户端基于已知 baseUri 拼接")
    private String loginPath;

    @JsonProperty("loginUrl")
    @JsonPropertyDescription("绝对登录地址，供简单客户端直接打开")
    private String loginUrl;

    @JsonProperty("nextAction")
    @JsonPropertyDescription("建议调用方下一步执行的动作，例如 OPEN_LOGIN_URL、WAIT_AND_RETRY、RETRY、NONE")
    private String nextAction;

    @JsonProperty("retryable")
    @JsonPropertyDescription("是否适合重试")
    private Boolean retryable;
}
