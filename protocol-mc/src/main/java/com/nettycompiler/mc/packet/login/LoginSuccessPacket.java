package com.nettycompiler.mc.packet.login;

import com.nettycompiler.mc.protocol.McPacket;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;

/** LoginSuccessPacket (0x02, LOGIN, CLIENTBOUND) — transitions to PLAY state. */
public class LoginSuccessPacket extends McPacket {
    private UUID uuid = UUID.randomUUID();
    private String username = "";

    @Override public int getPacketId() { return 0x02; }

    @Override
    public void read(ByteBuf buf) {
        uuid = readUUID(buf);
        username = readString(buf);
    }

    @Override
    public void write(ByteBuf buf) {
        writeUUID(buf, uuid);
        writeString(buf, username);
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of("uuid", uuid.toString(), "username", username);
    }

    @Override
    public void fromMap(Map<String, Object> fields) {
        this.uuid = UUID.fromString((String) fields.getOrDefault("uuid", UUID.randomUUID().toString()));
        this.username = (String) fields.getOrDefault("username", "");
    }

    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
