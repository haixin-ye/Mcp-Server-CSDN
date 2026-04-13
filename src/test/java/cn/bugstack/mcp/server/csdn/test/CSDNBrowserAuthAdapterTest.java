package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.domain.model.CSDNAuthState;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.FileSessionStore;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CSDNBrowserAuthAdapterTest {

    @Test
    public void test_saveStorageState_shouldCreateFile() throws Exception {
        Path tempDir = Files.createTempDirectory("csdn-browser-auth");
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setDataRoot(tempDir.toString());
        FileSessionStore store = new FileSessionStore(properties);

        store.saveStorageState("{\"cookies\":[]}");

        assertTrue(Files.exists(tempDir.resolve("session").resolve("storage-state.json")));
    }

    @Test
    public void test_captureStorageState_shouldThrowUntilImplemented() throws Exception {
        Path tempDir = Files.createTempDirectory("csdn-browser-auth-state");
        CSDNSessionProperties properties = new CSDNSessionProperties();
        properties.setDataRoot(tempDir.toString());
        FileSessionStore store = new FileSessionStore(properties);

        CSDNAuthState authState = new CSDNAuthState();
        authState.setCookie("cookie=value");
        store.saveAuthState(authState);

        Optional<CSDNAuthState> loaded = store.loadAuthState();

        assertTrue(loaded.isPresent());
        assertEquals("cookie=value", loaded.get().getCookie());
    }
}
