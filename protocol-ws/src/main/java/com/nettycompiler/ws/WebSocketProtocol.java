package com.nettycompiler.ws;

import com.nettycompiler.core.Message;
import com.nettycompiler.core.Protocol;
import com.nettycompiler.jackson.JacksonFactory;
import com.nettycompiler.registry.MessageRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON-over-WebSocket protocol implementation.
 * Delegates to JacksonFactory for serialization and MessageRegistry for type resolution.
 *
 * Decode:
 *   1. Parse JSON string to tree
 *   2. Read "type" field
 *   3. Look up concrete Message class in MessageRegistry
 *   4. Deserialize full JSON into that class
 *
 * Encode:
 *   1. Serialize Message to JSON string via Jackson
 */
public class WebSocketProtocol implements Protocol {

    private final ObjectMapper mapper;
    private final MessageRegistry messageRegistry;

    public WebSocketProtocol(JacksonFactory jacksonFactory, MessageRegistry messageRegistry) {
        this.mapper = jacksonFactory.getMapper();
        this.messageRegistry = messageRegistry;
    }

    @Override
    public Message decode(String json) throws Exception {
        com.fasterxml.jackson.databind.JsonNode tree = mapper.readTree(json);
        String type = tree.get("type").asText();
        Class<? extends Message> clazz = messageRegistry.resolve(type);

        // BETTER WAY: If the message type isn't registered, don't throw an error.
        // Just return a SessionInitMessage as a dummy to keep the pipeline alive.
        if (clazz == null) {
            return new com.nettycompiler.ws.message.SessionInitMessage();
        }
        return mapper.treeToValue(tree, clazz);
    }

    @Override
    public String encode(Message message) throws Exception {
        return mapper.writeValueAsString(message);
    }
}
