package com.nettycompiler.core;

/**
 * Session — one connected client over WebSocket.
 * Each session maps to one user, one Netty channel, and (eventually) one Docker container.
 */
public interface Session {

    /**
     * Unique identifier for this session.
     */
    String getId();

    /**
     * Send a message to the connected client.
     */
    void send(Message message);

    /**
     * Disconnect this session, closing the channel.
     */
    void disconnect();

    /**
     * Get the current connection state.
     */
    ConnectionState getState();

    /**
     * Transition this session to a new connection state.
     */
    void setState(ConnectionState state);
}
