package com.nettycompiler.registry;

import com.nettycompiler.core.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps message type strings to Message classes.
 * Used for Jackson deserialization routing and handler dispatch.
 * Thread-safe. All types must be registered before use (strict whitelist).
 */
public class MessageRegistry {

    private final Map<String, Class<? extends Message>> byType = new ConcurrentHashMap<>();

    public void register(String type, Class<? extends Message> clazz) {
        byType.put(type, clazz);
    }

    public Class<? extends Message> resolve(String type) {
        return byType.get(type);
    }

    public boolean isRegistered(String type) {
        return byType.containsKey(type);
    }

    public int size() {
        return byType.size();
    }
}
