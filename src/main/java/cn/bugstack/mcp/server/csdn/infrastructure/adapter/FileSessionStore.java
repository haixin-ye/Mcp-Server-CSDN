package cn.bugstack.mcp.server.csdn.infrastructure.adapter;

import cn.bugstack.mcp.server.csdn.domain.adapter.ISessionStore;
import cn.bugstack.mcp.server.csdn.domain.model.SessionMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Component
public class FileSessionStore implements ISessionStore {

    private static final String SESSION_DIRECTORY_NAME = "session";
    private static final String SESSION_METADATA_FILE_NAME = "session-meta.json";
    private static final String STORAGE_STATE_FILE_NAME = "storage-state.json";

    private final Path metadataPath;
    private final Path storageStatePath;
    private final ObjectMapper objectMapper;

    public FileSessionStore(@Value("${csdn.session.data-root:/app/data}") String dataRoot) {
        Path sessionRoot = Path.of(dataRoot).resolve(SESSION_DIRECTORY_NAME);
        this.metadataPath = sessionRoot.resolve(SESSION_METADATA_FILE_NAME);
        this.storageStatePath = sessionRoot.resolve(STORAGE_STATE_FILE_NAME);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public SessionMetadata loadMetadata() throws IOException {
        Files.createDirectories(storageStatePath.getParent());
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
}
