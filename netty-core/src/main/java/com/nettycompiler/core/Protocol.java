package com.nettycompiler.core;

import io.netty.buffer.ByteBuf;

/**
 * Protocol — turn bytes into packets.
 * This is the contract between Netty Core and any Protocol Plugin.
 * Implement this for MC, custom binary, or any future protocol.
 */
public interface Protocol {

    /**
     * Decode raw bytes into a typed Packet.
     */
    Packet decode(ByteBuf buf, ConnectionState state, PacketDirection direction);

    /**
     * Encode a typed Packet back into raw bytes.
     */
    void encode(Packet packet, ByteBuf buf);

    /**
     * Determine the next connection state after processing a packet.
     * This drives the state machine (HANDSHAKING → STATUS/LOGIN → PLAY).
     */
    ConnectionState nextState(Packet packet, ConnectionState current);
}
