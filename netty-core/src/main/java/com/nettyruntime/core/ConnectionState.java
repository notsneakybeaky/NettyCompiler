package com.nettyruntime.core;

/**
 * Connection states for the protocol state machine.
 * Generic enough for MC and extensible for custom protocols.
 */
public enum ConnectionState {
    HANDSHAKING,
    STATUS,
    LOGIN,
    PLAY
}
