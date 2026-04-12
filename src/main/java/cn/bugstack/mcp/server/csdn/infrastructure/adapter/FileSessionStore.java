package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import cn.bugstack.mcp.server.csdn.types.properties.CSDNSessionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
public class FileSessionStore implements ISessionStore {

    private static final String SESSION_DIRECTORY_NAME = "session";
    private static final String SESSION_METADATA_FILE_NAME = "session-meta.json";
    private static final String STORAGE_STATE_FILE_NAME = "storage-state.json";
    private static final Path DEFAULT_SESSION_ROOT = Path.of("/app/data").resolve(SESSION_DIRECTORY_NAME);
    private static final Path FALLBACK_SESSION_ROOT = Path.of("data").resolve(SESSION_DIRECTORY_NAME);

    private Path sessionRoot;
    private Path metadataPath;
    private Path storageStatePath;
    private final ObjectMapper objectMapper;

    public FileSessionStore(CSDNSessionProperties properties) {
        this.sessionRoot = Path.of(properties.getDataRoot()).resolve(SESSION_DIRECTORY_NAME);
        this.metadataPath = sessionRoot.resolve(SESSION_METADATA_FILE_NAME);
        this.storageStatePath = sessionRoot.resolve(STORAGE_STATE_FILE_NAME);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void initialize() throws IOException {
        try {
            Files.createDirectories(sessionRoot);
        } catch (IOException e) {
            if (DEFAULT_SESSION_ROOT.equals(sessionRoot)) {
                sessionRoot = FALLBACK_SESSION_ROOT;
                metadataPath = sessionRoot.resolve(SESSION_METADATA_FILE_NAME);
                storageStatePath = sessionRoot.resolve(STORAGE_STATE_FILE_NAME);
                Files.createDirectories(sessionRoot);
                return;
            }
            throw e;
        }
    }

    @Override
    public SessionMetadata loadMetadata() throws IOException {
        initialize();
        if (Files.notExists(metadataPath)) {
            SessionMetadata metadata = SessionMetadata.unbound();
            saveMetadata(metadata);
            return metadata;
        }

        return objectMapper.readValue(metadataPath.toFile(), SessionMetadata.class);
    }

    @Override
    public void saveMetadata(SessionMetadata metadata) throws IOException {
        Files.createDirectories(metadataPath.getParent());
        metadata.setUpdatedAt(LocalDateTime.now());
        objectMapper.writeValue(metadataPath.toFile(), metadata);
    }

    public void saveStorageState(String storageStateJson) throws IOException {
        initialize();
        Files.writeString(storageStatePath, storageStateJson, StandardCharsets.UTF_8);
    }
}
