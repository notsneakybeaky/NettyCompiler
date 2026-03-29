package com.nettycompiler.handler;

import com.nettycompiler.core.Message;
import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.core.Session;
import com.nettycompiler.jackson.JacksonFactory;
import com.nettycompiler.registry.MessageRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PythonHookBridge — implements MessageHandler by forwarding every hook call
 * to a per-session Python container over HTTP.
 *
 * Each session can be bound to its own container URL. Falls back to the
 * default URL if no per-session override is set.
 *
 * Hook endpoints:
 *   POST /hooks/connect
 *   POST /hooks/packet     (was /hooks/message — matches Python worker)
 *   POST /hooks/disconnect
 *   POST /scripts/load
 */
public class PythonHookBridge implements MessageHandler {

    private final HttpClient http;
    private final String defaultBaseUrl;
    private final ObjectMapper mapper;
    private final MessageRegistry messageRegistry;
    private final ConcurrentHashMap<String, String> sessionUrls = new ConcurrentHashMap<>();

    /**
     * @param defaultPythonUrl  fallback URL when no per-session URL is set
     * @param messageRegistry   registry for resolving message types from Python responses
     * @param jacksonFactory    configured Jackson factory for serialization
     */
    public PythonHookBridge(String defaultPythonUrl, MessageRegistry messageRegistry,
                            JacksonFactory jacksonFactory) {
        this.http = HttpClient.newBuilder().build();
        this.defaultBaseUrl = defaultPythonUrl;
        this.mapper = jacksonFactory.getMapper();
        this.messageRegistry = messageRegistry;
    }

    /**
     * Bind a session to a specific Python container URL.
     */
    public void setSessionUrl(String sessionId, String containerUrl) {
        sessionUrls.put(sessionId, containerUrl);
    }

    /**
     * Remove per-session URL binding (e.g. on disconnect).
     */
    public void removeSessionUrl(String sessionId) {
        sessionUrls.remove(sessionId);
    }

    private String baseUrlFor(Session session) {
        return sessionUrls.getOrDefault(session.getId(), defaultBaseUrl);
    }

    @Override
    public void onConnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", session.getId());
        body.put("hook", "on_connect");
        postAsync(baseUrlFor(session) + "/hooks/connect", body)
                .thenAccept(response -> handleActions(session, response))
                .exceptionally(err -> {
                    System.err.println("[PythonHookBridge] Connect hook failed for session "
                            + session.getId() + ": " + err.getMessage());
                    return null;
                });
    }

    @Override
    public void onMessage(Session session, Message message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("session_id", session.getId());
            body.put("hook", "on_packet");
            body.put("message_type", message.getType());
            body.put("payload", mapper.valueToTree(message));

            postAsync(baseUrlFor(session) + "/hooks/packet", body)
                    .thenAccept(response -> handleActions(session, response))
                    .exceptionally(err -> {
                        System.err.println("[PythonHookBridge] Hook call failed for session "
                                + session.getId() + ": " + err.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("[PythonHookBridge] Failed to serialize message: " + e.getMessage());
        }
    }

    @Override
    public void onDisconnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", session.getId());
        body.put("hook", "on_disconnect");
        postAsync(baseUrlFor(session) + "/hooks/disconnect", body);
        removeSessionUrl(session.getId());
    }

    /**
     * Load a script into the Python worker.
     */
    public CompletableFuture<String> loadScript(String scriptId, String source) {
        Map<String, Object> body = Map.of("script_id", scriptId, "source", source);
        return postAsync(defaultBaseUrl + "/scripts/load", body);
    }

    private CompletableFuture<String> postAsync(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            System.out.println("[PythonHookBridge] POST " + url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> {
                        System.out.println("[PythonHookBridge] " + url + " -> HTTP " + resp.statusCode());
                        return resp.body();
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process action list from Python response.
     * Actions: SEND, BLOCK, DISCONNECT
     */
    private void handleActions(Session session, String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode actions = root.get("actions");
            if (actions == null || !actions.isArray()) {
                System.out.println("[PythonHookBridge] No actions in response for session " + session.getId());
                return;
            }

            System.out.println("[PythonHookBridge] Processing " + actions.size() + " action(s) for session " + session.getId());
            for (JsonNode action : actions) {
                String type = action.get("type").asText();
                switch (type) {
                    case "SEND" -> {
                        Message message = reconstructMessage(action);
                        if (message != null) {
                            System.out.println("[PythonHookBridge] Sending " + message.getType() + " to session " + session.getId());
                            session.send(message);
                        } else {
                            System.err.println("[PythonHookBridge] SEND failed — could not reconstruct message from: " + action);
                        }
                    }
                    case "BLOCK" -> {
                        // Intentional no-op — message is dropped
                    }
                    case "DISCONNECT" -> session.disconnect();
                    default -> System.err.println("[PythonHookBridge] Unknown action type: " + type);
                }
            }
        } catch (Exception e) {
            System.err.println("[PythonHookBridge] Failed to parse actions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reconstruct a Message from a Python action's "message" field.
     * Uses MessageRegistry to resolve the type, then Jackson to deserialize.
     */
    private Message reconstructMessage(JsonNode action) {
        try {
            JsonNode messageNode = action.get("message");
            if (messageNode == null || !messageNode.isObject()) return null;

            JsonNode typeNode = messageNode.get("type");
            if (typeNode == null || typeNode.isNull()) return null;

            String messageType = typeNode.asText();
            Class<? extends Message> clazz = messageRegistry.resolve(messageType);
            if (clazz == null) {
                System.err.println("[PythonHookBridge] Unregistered message type: " + messageType);
                return null;
            }

            return mapper.treeToValue(messageNode, clazz);
        } catch (Exception e) {
            System.err.println("[PythonHookBridge] Failed to reconstruct message: " + e.getMessage());
            return null;
        }
    }
}