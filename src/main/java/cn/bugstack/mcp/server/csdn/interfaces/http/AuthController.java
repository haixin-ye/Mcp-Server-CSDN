package cn.bugstack.mcp.server.csdn.interfaces.http;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/auth/csdn/login")
    public AuthViewResponse login(@RequestParam("session") String sessionId) {
        AuthViewResponse response = new AuthViewResponse();
        response.setSession(sessionId);
        response.setMessage("请在此会话中完成 CSDN 登录");
        return response;
    }
}
