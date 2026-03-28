package com.nettycompiler.core;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Base class for all messages in the JSON-over-WebSocket protocol.
 * Every message has a type string used for routing and Jackson deserialization.
 * Subclasses are Jackson-serializable POJOs.
 */
public abstract class Message {

    /**
     * The message type identifier, used as the Jackson type discriminator.
     * Convention: lowercase_snake_case (e.g. "session_init", "domain_object").
     */
    @JsonProperty("type")
    public abstract String getType();
}
