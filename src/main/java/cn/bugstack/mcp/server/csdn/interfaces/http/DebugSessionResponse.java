package cn.bugstack.mcp.server.csdn.interfaces.http;

public class DebugSessionResponse {

    private String state;
    private String pendingLoginSessionId;
    private String pendingLoginUrl;
    private boolean authStatePresent;
    private int cookieLength;
    private String lastError;

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getPendingLoginSessionId() {
        return pendingLoginSessionId;
    }

    public void setPendingLoginSessionId(String pendingLoginSessionId) {
        this.pendingLoginSessionId = pendingLoginSessionId;
    }

    public String getPendingLoginUrl() {
        return pendingLoginUrl;
    }

    public void setPendingLoginUrl(String pendingLoginUrl) {
        this.pendingLoginUrl = pendingLoginUrl;
    }

    public boolean isAuthStatePresent() {
        return authStatePresent;
    }

    public void setAuthStatePresent(boolean authStatePresent) {
        this.authStatePresent = authStatePresent;
    }

    public int getCookieLength() {
        return cookieLength;
    }

    public void setCookieLength(int cookieLength) {
        this.cookieLength = cookieLength;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
