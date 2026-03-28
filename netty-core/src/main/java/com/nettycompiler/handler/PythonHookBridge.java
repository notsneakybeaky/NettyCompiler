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

/**
 * PythonHookBridge — implements MessageHandler by forwarding every hook call
 * to the Python worker over HTTP. This is the boundary between Netty Core
 * and Application Logic.
 *
 * Knows about: Session interface, HTTP, JSON contract, MessageRegistry.
 * Does NOT know about: protocol internals, Python internals, script registry.
 *
 * RECONSTRUCTION FLOW (SEND):
 *   Python returns: { "type": "SEND", "message": { "type": "...", ... } }
 *   Bridge does:
 *     1. Read "message.type" from JSON
 *     2. Look up in MessageRegistry -> get Message class
 *     3. Deserialize via Jackson into the concrete Message
 *     4. session.send(message) -> Netty encodes and writes
 */
public class PythonHookBridge implements MessageHandler {

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final MessageRegistry messageRegistry;

    /**
     * @param pythonWorkerUrl  base URL of the Python FastAPI worker (e.g. "http://localhost:8000")
     * @param messageRegistry  registry for resolving message types from Python responses
     * @param jacksonFactory   configured Jackson factory for serialization
     */
    public PythonHookBridge(String pythonWorkerUrl, MessageRegistry messageRegistry,
                            JacksonFactory jacksonFactory) {
        this.http = HttpClient.newBuilder().build();
        this.baseUrl = pythonWorkerUrl;
        this.mapper = jacksonFactory.getMapper();
        this.messageRegistry = messageRegistry;
    }

    @Override
    public void onConnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", session.getId());
        body.put("hook", "on_connect");
        postAsync(baseUrl + "/hooks/connect", body)
                .thenAccept(response -> handleActions(session, response));
    }

    @Override
    public void onMessage(Session session, Message message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("session_id", session.getId());
            body.put("hook", "on_message");
            body.put("message_type", message.getType());
            body.put("payload", mapper.valueToTree(message));

            postAsync(baseUrl + "/hooks/message", body)
                    .thenAccept(response -> handleActions(session, response));
        } catch (Exception e) {
            System.err.println("[PythonHookBridge] Failed to serialize message: " + e.getMessage());
        }
    }

    @Override
    public void onDisconnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session_id", session.getId());
        body.put("hook", "on_disconnect");
        postAsync(baseUrl + "/hooks/disconnect", body);
    }

    /**
     * Load a script into the Python worker.
     */
    public CompletableFuture<String> loadScript(String scriptId, String source) {
        Map<String, Object> body = Map.of("script_id", scriptId, "source", source);
        return postAsync(baseUrl + "/scripts/load", body);
    }

    private CompletableFuture<String> postAsync(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Process action list from Python response.
     * Actions: SEND, BLOCK, DISCONNECT
     *
     * JSON contract (Python -> Java):
     * {
     *   "actions": [
     *     { "type": "SEND", "message": { "type": "...", ... } },
     *     { "type": "BLOCK" },
     *     { "type": "DISCONNECT" }
     *   ]
     * }
     */
    private void handleActions(Session session, String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode actions = root.get("actions");
            if (actions == null || !actions.isArray()) return;

            for (JsonNode action : actions) {
                String type = action.get("type").asText();
                switch (type) {
                    case "SEND" -> {
                        Message message = reconstructMessage(action);
                        if (message != null) {
                            session.send(message);
                        } else {
                            System.err.println("[PythonHookBridge] SEND failed — could not reconstruct message");
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
