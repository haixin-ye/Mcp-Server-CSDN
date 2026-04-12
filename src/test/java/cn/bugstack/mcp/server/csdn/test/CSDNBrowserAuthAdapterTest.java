package cn.bugstack.mcp.server.csdn.test;

import cn.bugstack.mcp.server.csdn.infrastructure.adapter.CSDNBrowserAuthAdapter;
import cn.bugstack.mcp.server.csdn.infrastructure.adapter.FileSessionStore;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
    public void test_captureStorageState_shouldThrowUntilImplemented() {
        CSDNBrowserAuthAdapter adapter = new CSDNBrowserAuthAdapter();
        assertThrows(UnsupportedOperationException.class, () -> adapter.captureStorageState("http://127.0.0.1:18080/auth/csdn/login?session=test"));
    }
}
