package cn.bugstack.mcp.server.csdn.domain.service;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Cookie;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CSDNLoginSessionService {

    private static final String LOGIN_URL = "https://passport.csdn.net/login";
    private static final String EDITOR_URL = "https://editor.csdn.net/md";

    private final ISessionStore sessionStore;
    private final Map<String, RuntimeSession> runtimeSessions = new ConcurrentHashMap<>();

    public CSDNLoginSessionService(ISessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public byte[] getLoginSnapshot(String sessionId) throws IOException {
        RuntimeSession runtimeSession = runtimeSessions.computeIfAbsent(sessionId, this::createRuntimeSession);
        if (runtimeSession.failed) {
            throw new IOException("登录会话初始化失败，请刷新页面重试。");
        }

        synchronized (runtimeSession.monitor) {
            if (runtimeSession.closed) {
                throw new IOException("登录会话已关闭，请刷新页面重试。");
            }
            try {
                runtimeSession.page.waitForTimeout(300);
                return runtimeSession.page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            } catch (PlaywrightException e) {
                runtimeSessions.remove(sessionId);
                closeSession(runtimeSession);
                throw new IOException("登录页面正在刷新，请稍后重试。");
            }
        }
    }

    public LoginStatus checkLoginStatus(String sessionId) throws IOException {
        SessionMetadata metadata = sessionStore.loadMetadata();
        if (metadata != null && metadata.getState() == SessionState.ACTIVE) {
            CSDNAuthState authState = sessionStore.loadAuthState().orElse(null);
            if (authState != null && hasText(authState.getCookie())) {
                return new LoginStatus("ACTIVE", "登录成功，可继续调用 publishArticle。");
            }
            metadata.setState(SessionState.UNBOUND);
            metadata.setLastError("登录态文件缺失，请重新登录。");
            sessionStore.saveMetadata(metadata);
            return new LoginStatus("UNBOUND", "登录态缺失，请重新登录。");
        }

        RuntimeSession runtimeSession = runtimeSessions.get(sessionId);
        if (runtimeSession == null) {
            CSDNAuthState authState = sessionStore.loadAuthState().orElse(null);
            if (authState != null && hasText(authState.getCookie())) {
                metadata.setState(SessionState.ACTIVE);
                metadata.setLastError(null);
                sessionStore.saveMetadata(metadata);
                return new LoginStatus("ACTIVE", "登录成功，可继续调用 publishArticle。");
            }
            return new LoginStatus("LOGIN_PENDING", "请先打开登录页加载二维码。");
        }
        if (runtimeSession.failed) {
            return new LoginStatus("FAILED", "登录浏览器初始化失败，请刷新页面重试。");
        }

        synchronized (runtimeSession.monitor) {
            if (runtimeSession.closed) {
                return new LoginStatus("LOGIN_PENDING", "登录会话已关闭，请刷新页面重试。");
            }
            if (tryFinalizeLogin(sessionId, runtimeSession)) {
                return new LoginStatus("ACTIVE", "登录成功，可继续调用 publishArticle。");
            }
        }

        return new LoginStatus("LOGIN_PENDING", "等待扫码登录中。");
    }

    public void resetSession(String sessionId) {
        RuntimeSession runtimeSession = runtimeSessions.remove(sessionId);
        if (runtimeSession != null) {
            closeSession(runtimeSession);
        }
    }

    private RuntimeSession createRuntimeSession(String sessionId) {
        RuntimeSession runtimeSession = new RuntimeSession();
        try {
            runtimeSession.playwright = Playwright.create();
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setArgs(List.of(
                            "--disable-blink-features=AutomationControlled",
                            "--disable-dev-shm-usage",
                            "--no-sandbox"
                    ));
            try {
                runtimeSession.browser = runtimeSession.playwright.chromium().launch(launchOptions);
            } catch (Exception e) {
                runtimeSession.browser = runtimeSession.playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            }

            runtimeSession.context = runtimeSession.browser.newContext(
                    new Browser.NewContextOptions()
                            .setLocale("zh-CN")
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36")
            );
            runtimeSession.page = runtimeSession.context.newPage();
            runtimeSession.page.addInitScript("Object.defineProperty(navigator, 'webdriver', { get: () => undefined });");
            runtimeSession.page.navigate(LOGIN_URL, new Page.NavigateOptions().setTimeout(30_000));
            runtimeSession.page.waitForTimeout(2500);
            runtimeSession.createdAt = LocalDateTime.now();
        } catch (Exception e) {
            runtimeSession.failed = true;
            closeSession(runtimeSession);
        }
        return runtimeSession;
    }

    private boolean tryFinalizeLogin(String sessionId, RuntimeSession runtimeSession) throws IOException {
        if (runtimeSession.failed) {
            return false;
        }
        try {
            List<Cookie> cookies = runtimeSession.context.cookies();
            if (!containsLoginCookie(cookies)) {
                return false;
            }

            runtimeSession.page.navigate(EDITOR_URL, new Page.NavigateOptions().setTimeout(20_000));
            String currentUrl = runtimeSession.page.url();
            if (currentUrl != null && (currentUrl.contains("passport.csdn.net") || currentUrl.contains("/login"))) {
                return false;
            }

            cookies = runtimeSession.context.cookies();
            String cookieHeader = cookies.stream()
                    .filter(cookie -> hasText(cookie.name) && hasText(cookie.value))
                    .map(cookie -> cookie.name + "=" + cookie.value)
                    .collect(Collectors.joining("; "));
            if (!hasText(cookieHeader)) {
                return false;
            }

            CSDNAuthState authState = new CSDNAuthState();
            authState.setCookie(cookieHeader);
            sessionStore.saveAuthState(authState);

            SessionMetadata metadata = sessionStore.loadMetadata();
            metadata.setState(SessionState.ACTIVE);
            metadata.setLastValidatedAt(LocalDateTime.now());
            metadata.setLastError(null);
            metadata.setPendingLoginSessionId(null);
            metadata.setPendingLoginUrl(null);
            sessionStore.saveMetadata(metadata);

            runtimeSessions.remove(sessionId);
            closeSession(runtimeSession);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean containsLoginCookie(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            String name = cookie.name;
            if ("UserToken".equalsIgnoreCase(name)
                    || "UserName".equalsIgnoreCase(name)
                    || "UserInfo".equalsIgnoreCase(name)
                    || "UserNick".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void closeSession(RuntimeSession runtimeSession) {
        runtimeSession.closed = true;
        try {
            if (runtimeSession.context != null) {
                runtimeSession.context.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (runtimeSession.browser != null) {
                runtimeSession.browser.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (runtimeSession.playwright != null) {
                runtimeSession.playwright.close();
            }
        } catch (Exception ignored) {
        }
    }

    public static class LoginStatus {
        private final String status;
        private final String message;

        public LoginStatus(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }

    private static class RuntimeSession {
        private final Object monitor = new Object();
        private Playwright playwright;
        private Browser browser;
        private BrowserContext context;
        private Page page;
        private boolean failed;
        private boolean closed;
        private LocalDateTime createdAt;
    }
}
