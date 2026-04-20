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
                    resultRef.set(new BrowserPublishResult(
                            statusCode,
                            statusCode >= 200 && statusCode < 300,
                            body,
                            bodyText,
                            capturedCookieRef.get()
                    ));
                }
            });

            page.navigate(EDITOR_URL, new Page.NavigateOptions().setTimeout(45_000));
            page.waitForTimeout(3_000);
            log.info("browser publish page loaded, url={}", page.url());

            boolean titleFilled = fillTitle(page, articleRequestDTO.getTitle());
            MarkdownFillResult markdownFillResult = fillMarkdown(page, articleRequestDTO.getMarkdowncontent());
            log.info("browser publish fill result, titleFilled={}, markdownFilled={}, strategy={}",
                    titleFilled,
                    markdownFillResult.filled(),
                    markdownFillResult.strategy());

            if (!markdownFillResult.filled()) {
                log.warn("browser publish aborted because editor content could not be written");
                context.close();
                browser.close();
                return Optional.of(BrowserPublishResult.validationFailure("编辑器正文写入失败，已中止保存。", capturedCookieRef.get()));
            }

            page.waitForTimeout(800);

            boolean shortcutTriggered = triggerSaveShortcut(page);
            log.info("browser publish shortcut result, triggered={}", shortcutTriggered);

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
                ".article-bar__title input",
                "input[type='text']"
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

    private MarkdownFillResult fillMarkdown(Page page, String markdown) {
        if (!hasText(markdown)) {
            return new MarkdownFillResult(false, "empty");
        }

        String strategy = tryFillMarkdownPre(page, markdown);
        if (strategy != null) {
            return new MarkdownFillResult(true, strategy);
        }

        strategy = tryFillByEditorApi(page, markdown);
        if (strategy != null) {
            return new MarkdownFillResult(true, strategy);
        }

        strategy = tryFillByTextarea(page, markdown);
        if (strategy != null) {
            return new MarkdownFillResult(true, strategy);
        }

        return new MarkdownFillResult(false, "none");
    }

    private String tryFillMarkdownPre(Page page, String markdown) {
        String[] selectors = new String[] {
                "pre.editor__inner.markdown-highlighting",
                "pre.editor__inner",
                "pre[contenteditable='true']"
        };

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.isVisible()) {
                    locator.click();
                    page.keyboard().press("Control+A");
                    page.keyboard().press("Backspace");
                    page.evaluate(
                            "({ selector, content }) => {" +
                                    "const pre = document.querySelector(selector);" +
                                    "if (!pre) return false;" +
                                    "pre.focus();" +
                                    "pre.textContent = content;" +
                                    "pre.dispatchEvent(new InputEvent('input', { bubbles: true, data: content, inputType: 'insertText' }));" +
                                    "pre.dispatchEvent(new Event('change', { bubbles: true }));" +
                                    "return true;" +
                                    "}",
                            Map.of("selector", selector, "content", markdown)
                    );
                    page.waitForTimeout(300);
                    return selector + "#dom-write";
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String tryFillByEditorApi(Page page, String markdown) {
        try {
            Object result = page.evaluate(
                    "(content) => {" +
                            "const lines = content.split('\\n');" +
                            "try {" +
                            "  if (window.monaco && window.monaco.editor && typeof window.monaco.editor.getModels === 'function') {" +
                            "    const models = window.monaco.editor.getModels();" +
                            "    if (models && models.length > 0) {" +
                            "      models[0].setValue(content);" +
                            "      return 'monaco-model';" +
                            "    }" +
                            "  }" +
                            "} catch (e) {}" +
                            "try {" +
                            "  const cmElement = document.querySelector('.CodeMirror');" +
                            "  if (cmElement && cmElement.CodeMirror) {" +
                            "    cmElement.CodeMirror.setValue(content);" +
                            "    return 'codemirror-instance';" +
                            "  }" +
                            "} catch (e) {}" +
                            "try {" +
                            "  const textareas = Array.from(document.querySelectorAll('textarea'));" +
                            "  const best = textareas.find(el => (el.placeholder || '').includes('Markdown')) || textareas.find(el => el.clientHeight > 120) || textareas[0];" +
                            "  if (best) {" +
                            "    best.focus();" +
                            "    best.value = content;" +
                            "    best.dispatchEvent(new Event('input', { bubbles: true }));" +
                            "    best.dispatchEvent(new Event('change', { bubbles: true }));" +
                            "    return 'textarea-dom';" +
                            "  }" +
                            "} catch (e) {}" +
                            "return null;" +
                            "}",
                    markdown
            );
            return result == null ? null : String.valueOf(result);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String tryFillByTextarea(Page page, String markdown) {
        String[] selectors = new String[] {
                "textarea[placeholder*='Markdown']",
                "textarea[placeholder*='请输入内容']",
                ".CodeMirror textarea",
                ".monaco-editor textarea",
                ".editor textarea",
                "textarea"
        };

        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.isVisible()) {
                    locator.click();
                    locator.press("Control+A");
                    locator.fill("");
                    locator.type(markdown, new Locator.TypeOptions().setDelay(1));
                    return selector;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
                            "  .filter(text => text && text.length <= 30)" +
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

        public static BrowserPublishResult validationFailure(String message, String cookie) {
            ArticleResponseDTO body = new ArticleResponseDTO();
            body.setCode(422);
            body.setMsg(message);
            return new BrowserPublishResult(422, false, body, "{\"msg\":\"" + message + "\"}", cookie);
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

    private record MarkdownFillResult(boolean filled, String strategy) {
    }
}
