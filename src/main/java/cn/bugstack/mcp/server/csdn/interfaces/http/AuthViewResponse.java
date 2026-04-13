package cn.bugstack.mcp.server.csdn.interfaces.http;

import lombok.Data;

@Data
public class AuthViewResponse {

    private String session;
    private String message;

}
