package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleRequestDTO;
import cn.bugstack.mcp.server.csdn.infrastructure.gateway.dto.ArticleResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CSDNBrowserAuthAdapter {

    private static final String EDITOR_URL = "https://editor.csdn.net/md";
    private static final String SAVE_ARTICLE_URL_KEYWORD = "/blog-console-api/v3/mdeditor/saveArticle";
    private static final String CSDN_DOMAIN = ".csdn.net";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public Optional<BrowserPublishResult> publishArticleInBrowser(String cookieHeader, ArticleRequestDTO articleRequestDTO) {
        if (!hasText(cookieHeader)) {
            return Optional.empty();
        }

        List<Cookie> cookies = parseCookieHeader(cookieHeader);
        if (cookies.isEmpty()) {
            return Optional.empty();
        }

        AtomicReference<BrowserPublishResult> resultRef = new AtomicReference<>();
        AtomicReference<String> capturedCookieRef = new AtomicReference<>(cookieHeader);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setLocale("zh-CN")
                            .setUserAgent(USER_AGENT)
            );
            context.addCookies(cookies);

            Page page = context.newPage();
            page.onRequest(request -> {
                String reqCookie = request.headers().get("cookie");
                if (hasText(reqCookie)) {
                    capturedCookieRef.set(reqCookie);
                }
            });
            page.onResponse(response -> {
                String url = response.url();
                if (url != null && url.contains(SAVE_ARTICLE_URL_KEYWORD)) {
                    int statusCode = response.status();
                    String bodyText = "";
                    ArticleResponseDTO body = null;
                    try {
                        bodyText = response.text();
                        if (hasText(bodyText) && bodyText.trim().startsWith("{")) {
                            body = OBJECT_MAPPER.readValue(bodyText, ArticleResponseDTO.class);
                        }
                    } catch (Exception ignored) {
                    }
                    resultRef.set(new BrowserPublishResult(statusCode, statusCode >= 200 && statusCode < 300, body, bodyText, capturedCookieRef.get()));
                }
            });

            page.navigate(EDITOR_URL, new Page.NavigateOptions().setTimeout(45_000));
            page.waitForTimeout(3_000);
            log.info("browser publish page loaded, url={}", page.url());

            boolean titleFilled = fillTitle(page, articleRequestDTO.getTitle());
            boolean markdownFilled = fillMarkdown(page, articleRequestDTO.getMarkdowncontent());
            log.info("browser publish fill result, titleFilled={}, markdownFilled={}", titleFilled, markdownFilled);
            page.waitForTimeout(500);

            boolean shortcutTriggered = triggerSaveShortcut(page);
            log.info("browser publish shortcut result, triggered={}", shortcutTriggered);

            // Try common actions in order. Any one that triggers saveArticle is acceptable.
            boolean clicked = clickDraftSaveAction(page);
            if (!clicked) {
                clicked = clickAction(page, "保存草稿");
            }
            if (!clicked) {
                clicked = clickAction(page, "保存");
            }
            if (!clicked) {
                clicked = clickAction(page, "发布文章");
            }
            if (!clicked) {
                clicked = clickAction(page, "发布");
            }
            if (!clicked) {
                clicked = clickAnySaveLikeAction(page);
            }
            log.info("browser publish click result, clicked={}, visibleActions={}", clicked, listVisibleActionTexts(page));

            for (int i = 0; i < 40; i++) {
                BrowserPublishResult result = resultRef.get();
                if (result != null) {
                    context.close();
                    browser.close();
                    return Optional.of(result);
                }
                page.waitForTimeout(500);
            }

            context.close();
            browser.close();
            log.warn("browser publish did not observe saveArticle request, finalUrl={}", page.url());
        } catch (Exception e) {
            log.warn("browser publish failed before observing response", e);
            return Optional.empty();
        }

        return Optional.empty();
    }

    // Kept for retrofit fallback path compatibility.
    public Optional<CSDNDynamicHeaders> resolveDynamicHeaders(String cookieHeader) {
        return Optional.empty();
    }

    private boolean fillTitle(Page page, String title) {
        if (!hasText(title)) {
            return false;
        }
        String[] selectors = new String[] {
                "input[placeholder*='标题']",
                "input[placeholder*='请输入文章标题']",
                "input[type='text']",
                ".article-bar__title input"
        };
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.isVisible()) {
                    locator.click();
                    locator.fill(title);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean clickDraftSaveAction(Page page) {
        try {
            Locator primaryDraftButton = page.locator(
                    "button:has-text('保存草稿'), " +
                    "[role='button']:has-text('保存草稿')"
            ).first();
            if (primaryDraftButton.isVisible()) {
                primaryDraftButton.click(new Locator.ClickOptions().setTimeout(2_000));
                return true;
            }
        } catch (Exception ignored) {
        }

        try {
            Locator dropdownTrigger = page.locator(
                    "button:has-text('保存草稿') svg, " +
                    "button:has-text('保存草稿') i, " +
                    "button:has-text('保存草稿') [class*='arrow'], " +
                    "[role='button']:has-text('保存草稿') svg"
            ).first();
            if (dropdownTrigger.isVisible()) {
                dropdownTrigger.click(new Locator.ClickOptions().setTimeout(2_000));
                page.waitForTimeout(300);
            }
        } catch (Exception ignored) {
        }

        try {
            Locator dropdownItem = page.locator(
                    ".ant-dropdown-menu-item:has-text('保存草稿'), " +
                    ".ant-dropdown-menu-title-content:has-text('保存草稿'), " +
                    "li:has-text('保存草稿'), " +
                    "[role='menuitem']:has-text('保存草稿'), " +
                    "text=保存草稿"
            ).first();
            if (dropdownItem.isVisible()) {
                dropdownItem.click(new Locator.ClickOptions().setTimeout(2_000));
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean fillMarkdown(Page page, String markdown) {
        if (!hasText(markdown)) {
            return false;
        }
        try {
            Object filled = page.evaluate(
                    "(content) => {" +
                            "const selectors = [" +
                            "  \"textarea[placeholder*='Markdown']\"," +
                            "  \"textarea\"," +
                            "  \"[contenteditable='true']\"," +
                            "  \".CodeMirror textarea\"," +
                            "  \".monaco-editor textarea\"," +
                            "  \".editor textarea\"" +
                            "];" +
                            "for (const sel of selectors) {" +
                            "  const el = document.querySelector(sel);" +
                            "  if (!el) continue;" +
                            "  if (el.tagName === 'TEXTAREA' || el.tagName === 'INPUT') {" +
                            "    el.focus();" +
                            "    el.value = content;" +
                            "    el.dispatchEvent(new Event('input', { bubbles: true }));" +
                            "    el.dispatchEvent(new Event('change', { bubbles: true }));" +
                            "    return true;" +
                            "  }" +
                            "  el.focus();" +
                            "  el.innerText = content;" +
                            "  el.dispatchEvent(new InputEvent('input', { bubbles: true, data: content }));" +
                            "  return true;" +
                            "}" +
                            "return false;" +
                            "}",
                    markdown
            );
            if (Boolean.TRUE.equals(filled)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            Locator editable = page.locator("[contenteditable='true']").first();
            if (editable.isVisible()) {
                editable.click();
                page.keyboard().press("Control+A");
                page.keyboard().insertText(markdown);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean clickAction(Page page, String actionText) {
        try {
            Locator locator = page.locator(
                    "button:has-text('" + actionText + "'), " +
                    "[role='button']:has-text('" + actionText + "'), " +
                    "a:has-text('" + actionText + "'), " +
                    "span:has-text('" + actionText + "'), " +
                    "div:has-text('" + actionText + "')"
            ).first();
            if (locator.isVisible()) {
                locator.click(new Locator.ClickOptions().setTimeout(2_000));
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean triggerSaveShortcut(Page page) {
        try {
            page.bringToFront();
            page.keyboard().press("Control+S");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean clickAnySaveLikeAction(Page page) {
        String[] selectors = new String[] {
                "[class*='save']",
                "[class*='publish']",
                "[data-type*='save']",
                "[data-type*='publish']",
                ".toolbar button",
                ".editor-toolbar button",
                "header button",
                "button",
                "[role='button']"
        };
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.isVisible()) {
                    String text = locator.textContent();
                    if (text != null && (text.contains("保存") || text.contains("发布") || text.contains("草稿"))) {
                        locator.click(new Locator.ClickOptions().setTimeout(2_000));
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String listVisibleActionTexts(Page page) {
        try {
            Object value = page.evaluate(
                    "() => {" +
                            "const nodes = Array.from(document.querySelectorAll('button, [role=\"button\"], a, span, div'));" +
                            "return nodes" +
                            "  .map(node => (node.innerText || '').trim())" +
                            "  .filter(text => text && text.length <= 20)" +
                            "  .filter(text => /保存|发布|草稿|文章/.test(text))" +
                            "  .slice(0, 20);" +
                            "}"
            );
            return String.valueOf(value);
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private List<Cookie> parseCookieHeader(String cookieHeader) {
        List<Cookie> cookies = new ArrayList<>();
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int index = trimmed.indexOf('=');
            if (index <= 0 || index >= trimmed.length() - 1) {
                continue;
            }
            String name = trimmed.substring(0, index).trim();
            String value = trimmed.substring(index + 1).trim();
            if (!hasText(name) || !hasText(value)) {
                continue;
            }
            Cookie cookie = new Cookie(name, value);
            cookie.setDomain(CSDN_DOMAIN);
            cookie.setPath("/");
            cookie.setSecure(true);
            cookies.add(cookie);
        }
        return cookies;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public static class CSDNDynamicHeaders {
        private final String cookie;
        private final String nonce;
        private final String signature;

        public CSDNDynamicHeaders(String cookie, String nonce, String signature) {
            this.cookie = cookie;
            this.nonce = nonce;
            this.signature = signature;
        }

        public String getCookie() {
            return cookie;
        }

        public String getNonce() {
            return nonce;
        }

        public String getSignature() {
            return signature;
        }
    }

    public static class BrowserPublishResult {
        private final int statusCode;
        private final boolean successful;
        private final ArticleResponseDTO body;
        private final String rawBody;
        private final String cookie;

        public BrowserPublishResult(int statusCode, boolean successful, ArticleResponseDTO body, String rawBody, String cookie) {
            this.statusCode = statusCode;
            this.successful = successful;
            this.body = body;
            this.rawBody = rawBody;
            this.cookie = cookie;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public ArticleResponseDTO getBody() {
            return body;
        }

        public String getRawBody() {
            return rawBody;
        }

        public String getCookie() {
            return cookie;
        }
    }
}
