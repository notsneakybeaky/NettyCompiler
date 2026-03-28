package com.nettycompiler.core;

/**
 * Connection states for the protocol state machine.
 * Needs to be adjusted, this needs to create as many states as I want it to.
 */
public enum ConnectionState {
    HANDSHAKING,
    STATUS,
    LOGIN,
    PLAY
}
