package com.nettyruntime.mc.packet.status;

import com.nettyruntime.mc.protocol.McPacket;
import io.netty.buffer.ByteBuf;
import java.util.Map;

/** StatusResponsePacket (0x00, STATUS, CLIENTBOUND) — JSON server status. */
public class StatusResponsePacket extends McPacket {
    private String jsonResponse = "";

    @Override public int getPacketId() { return 0x00; }

    @Override public void read(ByteBuf buf) { jsonResponse = readString(buf); }
    @Override public void write(ByteBuf buf) { writeString(buf, jsonResponse); }

    @Override public Map<String, Object> toMap() { return Map.of("jsonResponse", jsonResponse); }
    @Override public void fromMap(Map<String, Object> fields) {
        this.jsonResponse = (String) fields.getOrDefault("jsonResponse", "");
    }

    public String getJsonResponse() { return jsonResponse; }
    public void setJsonResponse(String json) { this.jsonResponse = json; }
}
