package cn.bugstack.mcp.server.csdn.domain.adapter;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;

import java.io.IOException;

public interface ISessionStore {

    void initialize() throws IOException;

    SessionMetadata loadMetadata() throws IOException;

    void saveMetadata(SessionMetadata metadata) throws IOException;

}
