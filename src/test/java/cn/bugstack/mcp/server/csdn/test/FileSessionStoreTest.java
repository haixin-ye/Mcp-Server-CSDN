package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.domain.model.SessionState;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.FileSessionStore;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSessionStoreTest {

    @Test
    public void test_loadMetadata_shouldInitializeUnboundState() throws Exception {
        Path tempDir = Files.createTempDirectory("csdn-session-test");
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setDataRoot(tempDir.toString());
        FileSessionStore store = new FileSessionStore(properties);
        Path metadataPath = tempDir.resolve("session").resolve("session-meta.json");

        SessionMetadata metadata = store.loadMetadata();

        assertEquals(SessionState.UNBOUND, metadata.getState());
        assertTrue(Files.exists(metadataPath));
    }
}
