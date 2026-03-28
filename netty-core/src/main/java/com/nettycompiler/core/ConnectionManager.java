package com.nettycompiler.core;

import com.nettycompiler.session.DefaultSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConnectionManager — tracks all active sessions.
 * Thread-safe. Netty Core only.
 */
public class ConnectionManager {

    private final Map<String, DefaultSession> sessions = new ConcurrentHashMap<>();

    public void addSession(DefaultSession session) {
        sessions.put(session.getId(), session);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    public DefaultSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Collection<DefaultSession> getAllSessions() {
        return sessions.values();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Broadcast a packet to all connected sessions.
     */
    public void broadcast(Packet packet) {
        for (DefaultSession session : sessions.values()) {
            session.send(packet);
        }
    }
}
