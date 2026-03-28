package com.nettycompiler.core;

/**
 * Connection states for the WebSocket session lifecycle.
 */
public enum ConnectionState {
    CONNECTING,      // WebSocket handshake in progress
    AUTHENTICATING,  // Session init / auth exchange (future)
    INITIALIZING,    // Docker container being provisioned
    ACTIVE,          // Normal message exchange
    CLOSING          // Graceful shutdown in progress
}
