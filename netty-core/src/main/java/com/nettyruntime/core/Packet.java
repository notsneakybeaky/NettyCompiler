package com.nettyruntime.core;

import java.util.Map;

/**
 * Base class for all packets across all protocols.
 * Each protocol plugin defines concrete packet types extending this.
 *
 * CONTRACT:
 *   toMap()  → serializes fields to a flat map for JSON transport to Python
 *   fromMap() → populates fields from a flat map received back from Python
 *
 * These two methods are inverses. If toMap() emits {"username":"Steve","uuid":"..."}
 * then fromMap() must accept that same map and restore the packet's state.
 * Protocol plugins implement both. Netty Core calls both. Neither knows the other exists.
 */
public abstract class Packet {

    /**
     * The numeric packet ID within the protocol.
     */
    public abstract int getPacketId();

    /**
     * Serialize this packet's fields to a map for JSON transport to Python.
     */
    public abstract Map<String, Object> toMap();

    /**
     * Populate this packet's fields from a map received from Python.
     * This is the inverse of toMap(). The map keys and value types
     * must match what toMap() produces.
     *
     * @param fields the flat key-value map from the Python response payload
     */
    public abstract void fromMap(Map<String, Object> fields);

    /**
     * Human-readable packet type name (used in hook dispatch and reverse lookup).
     */
    public String getTypeName() {
        return getClass().getSimpleName();
    }
}
