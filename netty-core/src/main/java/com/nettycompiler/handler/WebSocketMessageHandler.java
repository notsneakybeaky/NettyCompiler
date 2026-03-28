package com.nettycompiler.handler;

import com.nettycompiler.core.ConnectionState;
import com.nettycompiler.core.Message;
import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.session.DefaultSession;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Bridges Netty channel events to the MessageHandler interface.
 * Receives decoded Message objects from JsonMessageCodec.
 * Connection lifecycle is handled here, business logic routing is not.
 */
public class WebSocketMessageHandler extends SimpleChannelInboundHandler<Message> {

    private final DefaultSession session;
    private final MessageHandler messageHandler;

    public WebSocketMessageHandler(DefaultSession session, MessageHandler messageHandler) {
        this.session = session;
        this.messageHandler = messageHandler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        session.setState(ConnectionState.ACTIVE);
        messageHandler.onConnect(session);
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) {
        messageHandler.onMessage(session, message);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        session.setState(ConnectionState.CLOSING);
        messageHandler.onDisconnect(session);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[WebSocketMessageHandler] Error in session " + session.getId()
            + ": " + cause.getMessage());
        cause.printStackTrace();
        session.disconnect();
    }
}
