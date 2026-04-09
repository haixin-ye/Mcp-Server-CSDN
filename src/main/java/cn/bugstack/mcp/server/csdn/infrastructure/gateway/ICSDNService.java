package cn.bugstack.mcp.server.csdn.infrastructure.gateway;

import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface ICSDNService {

    @Headers({
            "accept: */*",
            "accept-language:zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6",
            "cache-control:no-cache",
            "content-type: application/json",
            "origin: https://editor.csdn.net",
            "pragma:no-cache",
            "priority: u=1, i",
            "referer: https://editor.csdn.net/",
            "sec-ch-ua: \"Not:A-Brand\";v=\"99\", \"Microsoft Edge\";v=\"145\", \"Chromium\";v=\"145\"",
            "sec-ch-ua-mobile: ?0",
            "sec-ch-ua-platform: \"Windows\"",
            "sec-fetch-dest: empty",
            "sec-fetch-mode: cors",
            "sec-fetch-site: same-site",
            "user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0",
            "x-ca-key: 203803574",
            // 注意：x-ca-nonce 和 x-ca-signature 已从这里移除，改为动态传入
            "x-ca-signature-headers: x-ca-key,x-ca-nonce"
    })
    @POST("/blog-console-api/v3/mdeditor/saveArticle")
    Call<ArticleResponseDTO> saveArticle(
            @Body ArticleRequestDTO request,
            @Header("Cookie") String cookieValue,
            @Header("x-ca-nonce") String nonce,
            @Header("x-ca-signature") String signature
    );

}