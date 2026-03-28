package com.nettycompiler.mc.packet.status;

import com.nettycompiler.mc.protocol.McPacket;
import io.netty.buffer.ByteBuf;
import java.util.Map;

/** PongPacket (0x01, STATUS, CLIENTBOUND) */
public class PongPacket extends McPacket {
    private long payload;

    @Override public int getPacketId() { return 0x01; }
    @Override public void read(ByteBuf buf) { payload = buf.readLong(); }
    @Override public void write(ByteBuf buf) { buf.writeLong(payload); }
    @Override public Map<String, Object> toMap() { return Map.of("payload", payload); }
    @Override public void fromMap(Map<String, Object> fields) {
        Object val = fields.get("payload");
        this.payload = val instanceof Number n ? n.longValue() : 0L;
    }

    public long getPayload() { return payload; }
    public void setPayload(long payload) { this.payload = payload; }
}
