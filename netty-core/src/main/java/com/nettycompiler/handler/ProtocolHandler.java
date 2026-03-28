package com.nettycompiler.handler;

import com.nettycompiler.core.*;
import com.nettycompiler.session.DefaultSession;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * ProtocolHandler — bridges Netty channel events to the PacketHandler interface.
 * Receives decoded Packet objects from PacketCodec.
 * Delegates to PacketHandler (which may be PythonHookBridge or any other impl).
 * Manages state transitions via Protocol.nextState().
 */
public class ProtocolHandler extends SimpleChannelInboundHandler<Packet> {

    private final DefaultSession session;
    private final PacketHandler packetHandler;
    private final Protocol protocol;

    public ProtocolHandler(DefaultSession session, PacketHandler packetHandler, Protocol protocol) {
        this.session = session;
        this.packetHandler = packetHandler;
        this.protocol = protocol;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        packetHandler.onConnect(session);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // Advance state machine
        ConnectionState nextState = protocol.nextState(packet, session.getState());
        if (nextState != session.getState()) {
            session.setState(nextState);
        }

        // Delegate to application logic
        packetHandler.onPacket(session, packet);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        packetHandler.onDisconnect(session);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        session.disconnect();
    }
}
