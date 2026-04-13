package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.infrastructure.adapter.FileSessionStore;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SessionBootstrapTest {

    @Test
    public void test_storeInitialization_shouldCreateSessionDirectory() throws Exception {
        Path tempDir = Files.createTempDirectory("csdn-bootstrap-test");
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setDataRoot(tempDir.toString());
        FileSessionStore store = new FileSessionStore(properties);

        store.initialize();

        assertTrue(Files.exists(tempDir.resolve("session")));
    }
}
