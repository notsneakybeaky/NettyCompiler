package com.nettycompiler.mc.protocol;

import com.nettycompiler.core.Packet;
import io.netty.buffer.ByteBuf;

/**
 * McPacket — base class for all Minecraft packets.
 * Adds read/write methods for ByteBuf serialization.
 */
public abstract class McPacket extends Packet {

    public abstract void read(ByteBuf buf);
    public abstract void write(ByteBuf buf);

    // --- MC wire format helpers ---

    protected static String readString(ByteBuf buf) {
        int length = MinecraftProtocol.readVarInt(buf);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    protected static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MinecraftProtocol.writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    protected static java.util.UUID readUUID(ByteBuf buf) {
        long most = buf.readLong();
        long least = buf.readLong();
        return new java.util.UUID(most, least);
    }

    protected static void writeUUID(ByteBuf buf, java.util.UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }
}
