package com.nettycompiler.core;

/**
 * Protocol — contract for message serialization over a transport.
 * The WebSocket implementation uses Jackson for JSON encode/decode.
 */
public interface Protocol {

    /**
     * Decode a JSON string into a typed Message.
     */
    Message decode(String json) throws Exception;

    /**
     * Encode a typed Message into a JSON string.
     */
    String encode(Message message) throws Exception;
}
