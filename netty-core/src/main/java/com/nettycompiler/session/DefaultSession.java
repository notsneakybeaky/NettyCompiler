package com.nettycompiler.session;

import com.nettycompiler.core.*;

import io.netty.channel.Channel;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DefaultSession — concrete Session implementation owning both proxy legs.
 * Inbound channel: client → proxy.
 * Outbound channel: proxy → upstream server (null in standalone mode).
 */
public class DefaultSession implements Session {

    private final String id;
    private final Channel inboundChannel;
    private volatile Channel outboundChannel;
    private volatile ConnectionState state;
    private final Protocol protocol;
    private final AtomicInteger tick = new AtomicInteger(0);

    public DefaultSession(Channel inboundChannel, Protocol protocol) {
        this.id = UUID.randomUUID().toString();
        this.inboundChannel = inboundChannel;
        this.protocol = protocol;
        this.state = ConnectionState.HANDSHAKING;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void send(Packet packet) {
        if (inboundChannel != null && inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(packet);
        }
    }

    @Override
    public void forward(Packet packet) {
        if (outboundChannel != null && outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(packet);
        }
    }

    @Override
    public void disconnect() {
        if (inboundChannel != null && inboundChannel.isActive()) {
            inboundChannel.close();
        }
        if (outboundChannel != null && outboundChannel.isActive()) {
            outboundChannel.close();
        }
    }

    @Override
    public ConnectionState getState() {
        return state;
    }

    @Override
    public void setState(ConnectionState state) {
        this.state = state;
    }

    @Override
    public int getTick() {
        return tick.get();
    }

    public int incrementTick() {
        return tick.incrementAndGet();
    }

    public void setOutboundChannel(Channel outboundChannel) {
        this.outboundChannel = outboundChannel;
    }

    public Channel getInboundChannel() {
        return inboundChannel;
    }

    public Channel getOutboundChannel() {
        return outboundChannel;
    }

    public Protocol getProtocol() {
        return protocol;
    }
}
