package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;

/**
 * Sent by client upon WebSocket connect to identify itself.
 */
public class SessionInitMessage extends Message {

    private String clientId;
    private String metadata;

    @Override
    public String getType() {
        return "session_init";
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
