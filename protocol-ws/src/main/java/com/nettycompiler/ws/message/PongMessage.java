package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;

/**
 * Application-level keepalive pong response.
 */
public class PongMessage extends Message {

    private long timestamp;

    public PongMessage() {}

    public PongMessage(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String getType() {
        return "pong";
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
