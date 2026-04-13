package cn.bugstack.mcp.server.csdn.interfaces.http;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.domain.service.CSDNLoginSessionService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
public class AuthController {

    @Resource
    private ISessionStore sessionStore;

    @Resource
    private CSDNLoginSessionService loginSessionService;

    @GetMapping(value = "/auth/csdn/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(@RequestParam("session") String sessionId) throws IOException {
        SessionMetadata metadata = sessionStore.loadMetadata();
        metadata.setState(SessionState.LOGIN_PENDING);
        metadata.setPendingLoginSessionId(sessionId);
        metadata.setPendingLoginUrl("/auth/csdn/login?session=" + sessionId);
        metadata.setLastError(null);
        metadata.setLastValidatedAt(LocalDateTime.now());
        sessionStore.saveMetadata(metadata);
        return buildLoginPage(sessionId);
    }

    @GetMapping(value = "/auth/csdn/login/snapshot", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> snapshot(@RequestParam("session") String sessionId) throws IOException {
        try {
            byte[] png = loginSessionService.getLoginSnapshot(sessionId);
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("登录已完成")) {
                return ResponseEntity.noContent().build();
            }
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @GetMapping(value = "/auth/csdn/login/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> status(@RequestParam("session") String sessionId) throws IOException {
        CSDNLoginSessionService.LoginStatus loginStatus = loginSessionService.checkLoginStatus(sessionId);
        Map<String, Object> result = new HashMap<>();
        result.put("status", loginStatus.getStatus());
        result.put("message", loginStatus.getMessage());
        return result;
    }

    @PostMapping(value = "/auth/csdn/logout", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> logout(@RequestParam(value = "session", required = false) String sessionId) throws IOException {
        if (sessionId != null && !sessionId.isBlank()) {
            loginSessionService.resetSession(sessionId);
        }
        sessionStore.clearAuthState();
        SessionMetadata metadata = sessionStore.loadMetadata();
        metadata.setState(SessionState.UNBOUND);
        metadata.setPendingLoginSessionId(null);
        metadata.setPendingLoginUrl(null);
        metadata.setLastError(null);
        sessionStore.saveMetadata(metadata);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "UNBOUND");
        result.put("message", "已退出登录，请重新触发 publishArticle 获取登录链接。");
        return result;
    }

    private String buildLoginPage(String sessionId) {
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>CSDN 登录授权</title>
                  <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; line-height: 1.5; }
                    .box { max-width: 960px; margin: 0 auto; }
                    .hint { color: #444; }
                    img { width: 100%%; border: 1px solid #ddd; border-radius: 8px; background: #f6f8fa; }
                    code { background: #f6f8fa; padding: 2px 4px; border-radius: 4px; }
                    .actions { margin-top: 12px; display: flex; gap: 8px; }
                    button { padding: 8px 14px; }
                  </style>
                </head>
                <body>
                  <div class="box">
                    <h2>CSDN 登录授权</h2>
                    <p>会话ID：<strong>%s</strong></p>
                    <p class="hint">请直接扫码下方页面中的 CSDN 登录二维码。登录成功后，服务会自动保存会话，无需手动粘贴 Cookie。</p>
                    <img id="snapshot" src="/auth/csdn/login/snapshot?session=%s&t=0" alt="login snapshot"/>
                    <p id="status">状态：等待登录...</p>
                    <div class="actions">
                      <button id="relogin">重新登录</button>
                      <button id="logout">退出登录</button>
                    </div>
                    <p class="hint">如果图片未刷新，请保留当前页面 5-10 秒后再试。</p>
                  </div>
                  <script>
                    const sessionId = %s;
                    let tick = 1;
                    const statusEl = document.getElementById('status');
                    const imageEl = document.getElementById('snapshot');
                    let snapshotTimer = setInterval(() => {
                      imageEl.src = '/auth/csdn/login/snapshot?session=' + encodeURIComponent(sessionId) + '&t=' + (tick++);
                    }, 3000);
                    let statusTimer = setInterval(async () => {
                      const resp = await fetch('/auth/csdn/login/status?session=' + encodeURIComponent(sessionId));
                      const data = await resp.json();
                      statusEl.textContent = '状态：' + data.status + ' - ' + data.message;
                      if (data.status === 'ACTIVE') {
                        clearInterval(snapshotTimer);
                        clearInterval(statusTimer);
                        imageEl.style.display = 'none';
                        statusEl.innerHTML += '<br/>可返回 Agent 继续调用 <code>publishArticle</code>。';
                      }
                    }, 2500);
                    document.getElementById('relogin').addEventListener('click', () => {
                      window.location.href = '/auth/csdn/login?session=' + encodeURIComponent(sessionId);
                    });
                    document.getElementById('logout').addEventListener('click', async () => {
                      await fetch('/auth/csdn/logout?session=' + encodeURIComponent(sessionId), { method: 'POST' });
                      clearInterval(snapshotTimer);
                      clearInterval(statusTimer);
                      statusEl.textContent = '状态：UNBOUND - 已退出登录。';
                      imageEl.style.display = 'none';
                    });
                  </script>
                </body>
                </html>
                """.formatted(escapeHtml(sessionId), escapeHtml(sessionId), toJsStringLiteral(sessionId));
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String toJsStringLiteral(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
