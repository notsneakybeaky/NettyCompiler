package com.nettyruntime.codec;

import com.nettyruntime.core.*;
import com.nettyruntime.session.DefaultSession;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;

/**
 * PacketCodec — generalized codec that delegates to the Protocol interface.
 * Decodes inbound bytes → Packet objects.
 * Encodes outbound Packet objects → bytes.
 * Knows nothing about MC or any specific protocol.
 */
public class PacketCodec extends ByteToMessageCodec<Packet> {

    private final Protocol protocol;
    private final PacketDirection direction;
    private final DefaultSession session;

    public PacketCodec(Protocol protocol, PacketDirection direction, DefaultSession session) {
        this.protocol = protocol;
        this.direction = direction;
        this.session = session;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        protocol.encode(packet, out);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) return;

        in.markReaderIndex();
        try {
            Packet packet = protocol.decode(in, session.getState(), direction);
            if (packet != null) {
                out.add(packet);
            }
        } catch (Exception e) {
            in.resetReaderIndex();
            throw e;
        }
    }
}
