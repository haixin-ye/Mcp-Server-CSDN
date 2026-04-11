package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.FileSessionStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSessionStoreTest {

    @Test
    public void test_loadMetadata_shouldInitializeUnboundState() throws Exception {
        Path tempDir = Files.createTempDirectory("csdn-session-test");
        FileSessionStore store = new FileSessionStore(tempDir.toString());
        Path metadataPath = tempDir.resolve("session").resolve("session-meta.json");

        SessionMetadata metadata = store.loadMetadata();

        assertEquals(SessionState.UNBOUND, metadata.getState());
        assertTrue(Files.exists(metadataPath));
    }
}
