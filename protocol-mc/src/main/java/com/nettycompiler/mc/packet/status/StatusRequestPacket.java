package com.nettycompiler.mc.packet.status;

import com.nettycompiler.mc.protocol.McPacket;
import io.netty.buffer.ByteBuf;
import java.util.Map;

/** StatusRequestPacket (0x00, STATUS, SERVERBOUND) — empty packet. */
public class StatusRequestPacket extends McPacket {
    @Override public int getPacketId() { return 0x00; }
    @Override public void read(ByteBuf buf) {}
    @Override public void write(ByteBuf buf) {}
    @Override public Map<String, Object> toMap() { return Map.of(); }
    @Override public void fromMap(Map<String, Object> fields) {}
}
