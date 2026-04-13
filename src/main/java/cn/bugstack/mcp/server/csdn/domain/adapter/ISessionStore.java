package cn.bugstack.mcp.server.csdn.domain.adapter;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;

import java.io.IOException;
import java.util.Optional;

public interface ISessionStore {

    void initialize() throws IOException;

    SessionMetadata loadMetadata() throws IOException;

    void saveMetadata(SessionMetadata metadata) throws IOException;

    Optional<CSDNAuthState> loadAuthState() throws IOException;

    void saveAuthState(CSDNAuthState authState) throws IOException;

    void clearAuthState() throws IOException;

}
