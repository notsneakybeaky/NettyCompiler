package com.nettycompiler.ws.message;

import com.nettycompiler.core.Message;

/**
 * Server response confirming session creation.
 * Status may be "active" (container ready) or "initializing" (container starting).
 */
public class SessionAckMessage extends Message {

    private String sessionId;
    private String status;

    public SessionAckMessage() {}

    public SessionAckMessage(String sessionId, String status) {
        this.sessionId = sessionId;
        this.status = status;
    }

    @Override
    public String getType() {
        return "session_ack";
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
