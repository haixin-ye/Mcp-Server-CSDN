package cn.bugstack.mcp.server.csdn.infrastructure.transport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ServerMcpTransport;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class SafeWebMvcSseServerTransport implements ServerMcpTransport {

    private static final String DEFAULT_MESSAGE_ENDPOINT = "/mcp/message";
    private static final Duration SSE_SESSION_TIMEOUT = Duration.ofHours(12);

    private final ObjectMapper objectMapper;
    private final String messageEndpoint;
    private final String sseEndpoint;
    private final RouterFunction<ServerResponse> routerFunction;
    private final ConcurrentHashMap<String, ClientSession> sessions = new ConcurrentHashMap<>();

    private volatile boolean closing;
    private Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> connectHandler;

    public SafeWebMvcSseServerTransport(ObjectMapper objectMapper, String messageEndpoint) {
        this(objectMapper, messageEndpoint, WebMvcSseServerTransport.DEFAULT_SSE_ENDPOINT);
    }

    public SafeWebMvcSseServerTransport(ObjectMapper objectMapper, String messageEndpoint, String sseEndpoint) {
        this.objectMapper = objectMapper;
        this.messageEndpoint = hasText(messageEndpoint) ? messageEndpoint : DEFAULT_MESSAGE_ENDPOINT;
        this.sseEndpoint = hasText(sseEndpoint) ? sseEndpoint : WebMvcSseServerTransport.DEFAULT_SSE_ENDPOINT;
        this.routerFunction = RouterFunctions.route()
                .GET(this.sseEndpoint, this::handleSseConnection)
                .POST(this.messageEndpoint, this::handleMessage)
                .build();
    }

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> connectHandler) {
        this.connectHandler = connectHandler;
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.fromRunnable(() -> {
            if (sessions.isEmpty()) {
                log.debug("No active SSE sessions, skipping message broadcast.");
                return;
            }

            String payload;
            try {
                payload = objectMapper.writeValueAsString(message);
            } catch (IOException e) {
                log.error("Failed to serialize MCP message: {}", e.getMessage());
                return;
            }

            log.debug("Broadcasting MCP message to {} active SSE sessions.", sessions.size());
            for (Map.Entry<String, ClientSession> entry : sessions.entrySet()) {
                sendToSession(entry.getKey(), entry.getValue(), payload);
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return objectMapper.convertValue(data, typeRef);
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(() -> {
            closing = true;
            sessions.forEach((sessionId, session) -> {
                session.close();
                sessions.remove(sessionId);
            });
        });
    }

    public RouterFunction<ServerResponse> getRouterFunction() {
        return routerFunction;
    }

    private ServerResponse handleSseConnection(ServerRequest request) {
        if (closing) {
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
        }

        String sessionId = UUID.randomUUID().toString();
        log.info("Opening MCP SSE session: {}", sessionId);

        try {
            return ServerResponse.sse(builder -> {
                ClientSession session = new ClientSession(sessionId, builder);
                registerSession(session);

                builder.onTimeout(() -> {
                    log.warn("MCP SSE session timed out: {}", sessionId);
                    removeSession(sessionId, "timeout", null);
                });
                builder.onError(ex -> removeSession(sessionId, "error", ex));
                builder.onComplete(() -> removeSession(sessionId, "complete", null));
                try {
                    builder.id(sessionId)
                            .event(WebMvcSseServerTransport.ENDPOINT_EVENT_TYPE)
                            .data(messageEndpoint);
                } catch (IOException ex) {
                    removeSession(sessionId, "endpoint-send-failed", ex);
                    throw new IllegalStateException("Failed to send initial SSE endpoint event", ex);
                }
            }, SSE_SESSION_TIMEOUT);
        } catch (Exception ex) {
            log.error("Failed to initialize SSE session {}: {}", sessionId, ex.getMessage());
            removeSession(sessionId, "init-failed", ex);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ServerResponse handleMessage(ServerRequest request) {
        if (closing) {
            return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("Server is shutting down");
        }

        try {
            String body = request.body(String.class);
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(objectMapper, body);
            if (connectHandler == null) {
                return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE).body("MCP transport is not connected");
            }
            connectHandler.apply(Mono.just(message)).block();
            return ServerResponse.ok().build();
        } catch (IllegalArgumentException | IOException ex) {
            log.error("Failed to deserialize MCP message: {}", ex.getMessage());
            return ServerResponse.badRequest().body(ex.getMessage());
        } catch (Exception ex) {
            log.error("Error handling MCP message: {}", ex.getMessage(), ex);
            return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
        }
    }

    private void sendToSession(String sessionId, ClientSession session, String payload) {
        try {
            session.builder.id(sessionId)
                    .event(WebMvcSseServerTransport.MESSAGE_EVENT_TYPE)
                    .data(payload);
        } catch (Exception ex) {
            removeSession(sessionId, "send-failed", ex);
        }
    }

    private void registerSession(ClientSession session) {
        sessions.put(session.id, session);
        log.info("Registered MCP SSE session: {}, activeSessions={}", session.id, sessions.size());
    }

    private void removeSession(String sessionId, String reason, Throwable throwable) {
        ClientSession session = sessions.remove(sessionId);
        if (session == null) {
            return;
        }

        if (throwable != null) {
            log.warn("Removing MCP SSE session {}, reason={}, message={}", sessionId, reason, throwable.getMessage());
        } else {
            log.info("Removing MCP SSE session {}, reason={}", sessionId, reason);
        }

        session.close();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ClientSession {
        private final String id;
        private final ServerResponse.SseBuilder builder;
        private volatile boolean closed;

        private ClientSession(String id, ServerResponse.SseBuilder builder) {
            this.id = id;
            this.builder = builder;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                builder.complete();
            } catch (Exception ignored) {
            }
        }
    }
}
