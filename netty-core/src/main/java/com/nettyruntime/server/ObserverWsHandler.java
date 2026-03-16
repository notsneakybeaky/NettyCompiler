package com.nettyruntime.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ObserverWsHandler — generalized WebSocket broadcast for dashboard observers.
 * Flutter dashboard connects here to receive real-time state updates.
 * Knows nothing about what data it broadcasts — just forwards JSON strings.
 */
public class ObserverWsHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final Set<Channel> observers = ConcurrentHashMap.newKeySet();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        observers.add(ctx.channel());
        System.out.println("[Observer] Dashboard connected: " + ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        // Dashboard can send commands — handle if needed
        String message = frame.text();
        System.out.println("[Observer] Received from dashboard: " + message);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        observers.remove(ctx.channel());
        System.out.println("[Observer] Dashboard disconnected: " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    /**
     * Broadcast a JSON message to all connected dashboard observers.
     */
    public static void broadcast(String json) {
        TextWebSocketFrame frame = new TextWebSocketFrame(json);
        for (Channel ch : observers) {
            if (ch.isActive()) {
                ch.writeAndFlush(frame.retainedDuplicate());
            }
        }
        frame.release();
    }

    public static int getObserverCount() {
        return observers.size();
    }
}
