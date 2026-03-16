package com.nettyruntime.core;

/**
 * Direction of packet flow relative to the proxy.
 * CLIENTBOUND: server → client (S2C)
 * SERVERBOUND: client → server (C2S)
 */
public enum PacketDirection {
    CLIENTBOUND,
    SERVERBOUND
}
