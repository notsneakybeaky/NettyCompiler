package com.nettycompiler.mc.protocol;

import com.nettycompiler.core.*;
import com.nettycompiler.mc.packet.handshaking.HandshakePacket;
import com.nettycompiler.mc.packet.status.StatusRequestPacket;
import com.nettycompiler.mc.packet.status.StatusResponsePacket;
import com.nettycompiler.mc.packet.status.PingPacket;
import com.nettycompiler.mc.packet.status.PongPacket;
import com.nettycompiler.mc.packet.login.LoginStartPacket;
import com.nettycompiler.mc.packet.login.LoginSuccessPacket;
import com.nettycompiler.registry.PacketRegistry;

import io.netty.buffer.ByteBuf;

/**
 * MinecraftProtocol — implements Protocol for Minecraft Java Edition.
 * Handles the MC state machine: HANDSHAKING → STATUS/LOGIN → PLAY.
 * Knows about ByteBuf, ConnectionState, PacketDirection.
 * Does NOT know about PacketHandler or Session internals.
 */
public class MinecraftProtocol implements Protocol {

    private final PacketRegistry registry;

    public MinecraftProtocol() {
        this.registry = new PacketRegistry();
        registerPackets();
    }

    /** Expose the registry so PythonHookBridge can do reverse name lookups. */
    public PacketRegistry getRegistry() {
        return registry;
    }

    private void registerPackets() {
        // HANDSHAKING — serverbound only
        registry.register(ConnectionState.HANDSHAKING, PacketDirection.SERVERBOUND, 0x00, HandshakePacket::new);

        // STATUS — both directions
        registry.register(ConnectionState.STATUS, PacketDirection.SERVERBOUND, 0x00, StatusRequestPacket::new);
        registry.register(ConnectionState.STATUS, PacketDirection.CLIENTBOUND, 0x00, StatusResponsePacket::new);
        registry.register(ConnectionState.STATUS, PacketDirection.SERVERBOUND, 0x01, PingPacket::new);
        registry.register(ConnectionState.STATUS, PacketDirection.CLIENTBOUND, 0x01, PongPacket::new);

        // LOGIN — both directions
        registry.register(ConnectionState.LOGIN, PacketDirection.SERVERBOUND, 0x00, LoginStartPacket::new);
        registry.register(ConnectionState.LOGIN, PacketDirection.CLIENTBOUND, 0x02, LoginSuccessPacket::new);

        // PLAY packets — add as needed, not all at once
    }

    @Override
    public Packet decode(ByteBuf buf, ConnectionState state, PacketDirection direction) {
        int length = readVarInt(buf);
        int packetId = readVarInt(buf);

        Packet packet = registry.createPacket(state, direction, packetId);
        if (packet == null) {
            // Unknown packet — skip remaining bytes for this packet
            buf.skipBytes(Math.max(0, length - varIntSize(packetId)));
            return null;
        }

        // Packet implementations read their own fields from buf
        if (packet instanceof McPacket mcPacket) {
            mcPacket.read(buf);
        }

        return packet;
    }

    @Override
    public void encode(Packet packet, ByteBuf buf) {
        if (packet instanceof McPacket mcPacket) {
            ByteBuf payload = buf.alloc().buffer();
            writeVarInt(payload, packet.getPacketId());
            mcPacket.write(payload);

            writeVarInt(buf, payload.readableBytes());
            buf.writeBytes(payload);
            payload.release();
        }
    }

    @Override
    public ConnectionState nextState(Packet packet, ConnectionState current) {
        if (packet instanceof HandshakePacket handshake) {
            return switch (handshake.getNextState()) {
                case 1 -> ConnectionState.STATUS;
                case 2 -> ConnectionState.LOGIN;
                default -> current;
            };
        }
        if (packet instanceof LoginSuccessPacket) {
            return ConnectionState.PLAY;
        }
        return current;
    }

    // --- VarInt helpers ---

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte currentByte;
        do {
            currentByte = buf.readByte();
            value |= (currentByte & 0x7F) << position;
            position += 7;
            if (position >= 32) throw new RuntimeException("VarInt too big");
        } while ((currentByte & 0x80) != 0);
        return value;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static int varIntSize(int value) {
        int size = 0;
        do {
            size++;
            value >>>= 7;
        } while (value != 0);
        return size;
    }
}
