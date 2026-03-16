package com.nettyruntime.mc.packet.handshaking;

import com.nettyruntime.mc.protocol.McPacket;
import com.nettyruntime.mc.protocol.MinecraftProtocol;
import io.netty.buffer.ByteBuf;

import java.util.Map;

/**
 * HandshakePacket (0x00, HANDSHAKING, SERVERBOUND)
 * First packet sent by client. Routes to STATUS or LOGIN.
 */
public class HandshakePacket extends McPacket {

    private int protocolVersion;
    private String serverAddress;
    private int serverPort;
    private int nextState; // 1 = STATUS, 2 = LOGIN

    @Override
    public int getPacketId() { return 0x00; }

    @Override
    public void read(ByteBuf buf) {
        protocolVersion = MinecraftProtocol.readVarInt(buf);
        serverAddress = readString(buf);
        serverPort = buf.readUnsignedShort();
        nextState = MinecraftProtocol.readVarInt(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        MinecraftProtocol.writeVarInt(buf, protocolVersion);
        writeString(buf, serverAddress);
        buf.writeShort(serverPort);
        MinecraftProtocol.writeVarInt(buf, nextState);
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of(
            "protocolVersion", protocolVersion,
            "serverAddress", serverAddress,
            "serverPort", serverPort,
            "nextState", nextState
        );
    }

    @Override
    public void fromMap(Map<String, Object> fields) {
        this.protocolVersion = ((Number) fields.getOrDefault("protocolVersion", 0)).intValue();
        this.serverAddress   = (String) fields.getOrDefault("serverAddress", "");
        this.serverPort      = ((Number) fields.getOrDefault("serverPort", 25565)).intValue();
        this.nextState       = ((Number) fields.getOrDefault("nextState", 1)).intValue();
    }

    public int getProtocolVersion() { return protocolVersion; }
    public String getServerAddress() { return serverAddress; }
    public int getServerPort() { return serverPort; }
    public int getNextState() { return nextState; }
}
