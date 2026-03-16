package com.nettyruntime.registry;

import com.nettyruntime.core.ConnectionState;
import com.nettyruntime.core.Packet;
import com.nettyruntime.core.PacketDirection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * PacketRegistry — maps (ConnectionState, PacketDirection, packetId) to packet classes.
 * This is the generalized registry that replaces the old flat Map<Integer, Class> approach.
 *
 * ALSO maintains a reverse index: typeName (String) → Supplier, so that the
 * PythonHookBridge can reconstruct a Packet from a Python response containing
 * only the class name and a payload map. The forward registry is for decoding
 * bytes off the wire. The reverse registry is for reconstructing packets from
 * JSON that crossed the Python boundary.
 */
public class PacketRegistry {

    /**
     * Composite key for packet lookup.
     */
    public record PacketKey(ConnectionState state, PacketDirection direction, int packetId) {}

    /** Forward: wire decode path. (state, direction, id) → factory */
    private final Map<PacketKey, Supplier<? extends Packet>> registry = new ConcurrentHashMap<>();

    /** Reverse: JSON reconstruct path. typeName → factory */
    private final Map<String, Supplier<? extends Packet>> byName = new ConcurrentHashMap<>();

    /**
     * Register a packet type for a given state, direction, and ID.
     * Also registers the type name for reverse lookup automatically.
     */
    public void register(ConnectionState state, PacketDirection direction, int packetId,
                         Supplier<? extends Packet> factory) {
        PacketKey key = new PacketKey(state, direction, packetId);
        registry.put(key, factory);

        // Derive the type name from a throwaway instance and index it.
        // This runs once per packet type at startup — not on the hot path.
        String typeName = factory.get().getTypeName();
        byName.put(typeName, factory);
    }

    /**
     * Create a new packet instance for the given key, or null if unregistered.
     * Used by PacketCodec when decoding bytes off the wire.
     */
    public Packet createPacket(ConnectionState state, PacketDirection direction, int packetId) {
        PacketKey key = new PacketKey(state, direction, packetId);
        Supplier<? extends Packet> factory = registry.get(key);
        return factory != null ? factory.get() : null;
    }

    /**
     * Create a new packet instance by type name, or null if unregistered.
     * Used by PythonHookBridge when reconstructing packets from Python responses.
     *
     * @param typeName the value that Packet.getTypeName() returns (e.g. "LoginSuccessPacket")
     */
    public Packet createPacketByName(String typeName) {
        Supplier<? extends Packet> factory = byName.get(typeName);
        return factory != null ? factory.get() : null;
    }

    /**
     * Check if a packet type is registered by composite key.
     */
    public boolean isRegistered(ConnectionState state, PacketDirection direction, int packetId) {
        return registry.containsKey(new PacketKey(state, direction, packetId));
    }

    /**
     * Check if a packet type name is registered for reverse lookup.
     */
    public boolean isRegisteredByName(String typeName) {
        return byName.containsKey(typeName);
    }

    public int size() {
        return registry.size();
    }
}
