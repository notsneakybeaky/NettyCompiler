package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;

/**
 * Application-level keepalive ping (separate from WebSocket ping/pong frames).
 */
public class PingMessage extends Message {

    private long timestamp;

    @Override
    public String getType() {
        return "ping";
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
