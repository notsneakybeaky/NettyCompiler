package com.nettyruntime.handler;

import com.nettyruntime.core.*;
import com.nettyruntime.registry.PacketRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * PythonHookBridge — implements PacketHandler by forwarding every hook call
 * to the Python worker over HTTP. This is the boundary between Netty Core
 * and Application Logic.
 *
 * Knows about: Session interface, HTTP, JSON contract, PacketRegistry (for reverse lookup).
 * Does NOT know about: MC protocol internals, Python internals, script registry.
 *
 * RECONSTRUCTION FLOW (SEND / FORWARD):
 *   Python returns: { "type": "SEND", "packetType": "LoginSuccessPacket", "payload": {...} }
 *   Bridge does:
 *     1. registry.createPacketByName("LoginSuccessPacket")  → empty Packet instance
 *     2. packet.fromMap(payload)                            → populated Packet instance
 *     3. session.send(packet) or session.forward(packet)    → Netty encodes and writes
 *
 *   The bridge never knows what fields LoginSuccessPacket has. It only knows
 *   that Packet has fromMap() and Session has send()/forward(). The protocol
 *   plugin owns the field layout. The bridge owns the dispatch.
 */
public class PythonHookBridge implements PacketHandler {

    private final HttpClient http;
    private final String baseUrl;
    private final ObjectMapper mapper;
    private final PacketRegistry packetRegistry;

    /**
     * @param pythonWorkerUrl  base URL of the Python FastAPI worker (e.g. "http://localhost:8000")
     * @param packetRegistry   the protocol's registry, needed to reconstruct packets from type names
     */
    public PythonHookBridge(String pythonWorkerUrl, PacketRegistry packetRegistry) {
        this.http = HttpClient.newBuilder().build();
        this.baseUrl = pythonWorkerUrl;
        this.mapper = new ObjectMapper();
        this.packetRegistry = packetRegistry;
    }

    @Override
    public void onConnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", session.getId());
        body.put("hook", "on_connect");
        postAsync(baseUrl + "/hooks/connect", body)
                .thenAccept(response -> handleActions(session, response));
    }

    @Override
    public void onPacket(Session session, Packet packet) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", session.getId());
        body.put("hook", "on_packet");
        body.put("packetType", packet.getTypeName());
        body.put("payload", packet.toMap());
        body.put("tick", session.getTick());

        postAsync(baseUrl + "/hooks/packet", body)
                .thenAccept(response -> handleActions(session, response));
    }

    @Override
    public void onDisconnect(Session session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", session.getId());
        body.put("hook", "on_disconnect");
        postAsync(baseUrl + "/hooks/disconnect", body);
    }

    @Override
    public void onTick(Session session, int tick) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", session.getId());
        body.put("hook", "on_tick");
        body.put("tick", tick);
        postAsync(baseUrl + "/hooks/tick", body)
                .thenAccept(response -> handleActions(session, response));
    }

    /**
     * Load a script into the Python worker.
     */
    public CompletableFuture<String> loadScript(String scriptId, String source) {
        Map<String, Object> body = Map.of("scriptId", scriptId, "source", source);
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
     * Actions: SEND, FORWARD, BLOCK, DISCONNECT
     *
     * JSON contract (Python → Java):
     * {
     *   "actions": [
     *     {
     *       "type": "SEND | FORWARD | BLOCK | DISCONNECT",
     *       "packetType": "string | null",
     *       "payload": {}
     *     }
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
                        Packet packet = reconstructPacket(action);
                        if (packet != null) {
                            session.send(packet);
                        } else {
                            System.err.println("[PythonHookBridge] SEND failed — could not reconstruct packet: "
                                    + action.get("packetType"));
                        }
                    }
                    case "FORWARD" -> {
                        Packet packet = reconstructPacket(action);
                        if (packet != null) {
                            session.forward(packet);
                        } else {
                            System.err.println("[PythonHookBridge] FORWARD failed — could not reconstruct packet: "
                                    + action.get("packetType"));
                        }
                    }
                    case "BLOCK" -> {
                        // Intentional no-op — packet is dropped
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
     * Reconstruct a Packet from a Python action's packetType + payload.
     *
     * Steps:
     *   1. Read "packetType" string from the action node
     *   2. Look it up in the PacketRegistry reverse index (byName)
     *   3. Get an empty Packet instance from the factory
     *   4. Convert the "payload" JsonNode to a Map<String, Object>
     *   5. Call packet.fromMap(map) to populate its fields
     *   6. Return the fully populated Packet, ready for send() or forward()
     *
     * Returns null if packetType is missing, unregistered, or payload is absent.
     */
    private Packet reconstructPacket(JsonNode action) {
        JsonNode typeNode = action.get("packetType");
        if (typeNode == null || typeNode.isNull()) return null;

        String packetType = typeNode.asText();
        Packet packet = packetRegistry.createPacketByName(packetType);
        if (packet == null) return null;

        JsonNode payloadNode = action.get("payload");
        if (payloadNode == null || !payloadNode.isObject()) {
            // No payload — return the empty packet (some packets have no fields, e.g. StatusRequest)
            packet.fromMap(Map.of());
            return packet;
        }

        // Convert JsonNode → Map<String, Object> for the Packet.fromMap() contract.
        // This stays shallow intentionally. The payload map mirrors what toMap() produces:
        // flat key-value pairs where values are primitives (String, int, long, boolean)
        // or at most simple nested maps for compound fields. The protocol plugin's
        // fromMap() implementation knows its own field types and does the casting.
        Map<String, Object> fields = jsonNodeToMap(payloadNode);
        packet.fromMap(fields);
        return packet;
    }

    /**
     * Convert a Jackson JsonNode (object) to a flat Map<String, Object>.
     * Values are converted to their natural Java types:
     *   - text → String
     *   - integer → int or long (depending on size)
     *   - float → double
     *   - boolean → boolean
     *   - null → null
     *   - nested object → Map<String, Object> (recursive)
     *   - array → left as JsonNode (protocol plugins can handle if needed)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            map.put(entry.getKey(), jsonNodeToValue(entry.getValue()));
        }
        return map;
    }

    private Object jsonNodeToValue(JsonNode node) {
        if (node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isFloat() || node.isDouble()) return node.asDouble();
        if (node.isObject()) return jsonNodeToMap(node);
        // Arrays and other types — return raw text so fromMap() can parse if needed
        return node.toString();
    }
}
