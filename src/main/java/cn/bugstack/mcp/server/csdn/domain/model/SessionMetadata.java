package cn.bugstack.mcp.server.csdn.domain.model;

import java.time.LocalDateTime;

public class SessionMetadata {

    private SessionState state;
    private LocalDateTime updatedAt;
    private LocalDateTime lastValidatedAt;
    private String lastError;
    private String pendingLoginSessionId;
    private String pendingLoginUrl;

    public static SessionMetadata unbound() {
        SessionMetadata metadata = new SessionMetadata();
        metadata.setState(SessionState.UNBOUND);
        metadata.setUpdatedAt(LocalDateTime.now());
        return metadata;
    }

    public SessionState getState() {
        return state;
    }

    public void setState(SessionState state) {
        this.state = state;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastValidatedAt() {
        return lastValidatedAt;
    }

    public void setLastValidatedAt(LocalDateTime lastValidatedAt) {
        this.lastValidatedAt = lastValidatedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
}
