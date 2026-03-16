package com.nettyruntime.mc.packet.login;

import com.nettyruntime.mc.protocol.McPacket;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;

/** LoginStartPacket (0x00, LOGIN, SERVERBOUND) */
public class LoginStartPacket extends McPacket {
    private String username = "";
    private UUID playerUUID;

    @Override public int getPacketId() { return 0x00; }

    @Override
    public void read(ByteBuf buf) {
        username = readString(buf);
        if (buf.readableBytes() >= 16) {
            playerUUID = readUUID(buf);
        }
    }

    @Override
    public void write(ByteBuf buf) {
        writeString(buf, username);
        if (playerUUID != null) {
            writeUUID(buf, playerUUID);
        }
    }

    @Override
    public Map<String, Object> toMap() {
        return Map.of(
            "username", username,
            "uuid", playerUUID != null ? playerUUID.toString() : ""
        );
    }

    @Override
    public void fromMap(Map<String, Object> fields) {
        this.username = (String) fields.getOrDefault("username", "");
        String uuidStr = (String) fields.getOrDefault("uuid", "");
        this.playerUUID = uuidStr.isEmpty() ? null : UUID.fromString(uuidStr);
    }

    public String getUsername() { return username; }
    public UUID getPlayerUUID() { return playerUUID; }
}
