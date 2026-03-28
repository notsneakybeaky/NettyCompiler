package com.nettycompiler.core;

/**
 * Session — one connected client, owns both legs for proxy mode.
 * Inbound leg: client → proxy. Outbound leg: proxy → upstream server.
 * Either leg can intercept, mutate, block, or respond via Python hooks.
 */
public interface Session {

    /**
     * Unique identifier for this session.
     */
    String getId();

    /**
     * Send a packet back to the connected client (inbound leg).
     */
    void send(Packet packet);

    /**
     * Forward a packet to the upstream server (outbound leg).
     */
    void forward(Packet packet);

    /**
     * Disconnect this session, closing both legs.
     */
    void disconnect();

    /**
     * Get the current connection state (HANDSHAKING, STATUS, LOGIN, PLAY).
     */
    ConnectionState getState();

    /**
     * Transition this session to a new connection state.
     */
    void setState(ConnectionState state);

    /**
     * Get the current tick count for this session.
     */
    int getTick();
}
