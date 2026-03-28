package com.nettycompiler.session;

import com.nettycompiler.core.ConnectionState;
import com.nettycompiler.core.Message;
import com.nettycompiler.core.Session;

import io.netty.channel.Channel;

import java.util.UUID;

/**
 * DefaultSession — concrete Session implementation for WebSocket connections.
 * Each session maps to one Netty channel, one user, and (eventually) one Docker container.
 */
public class DefaultSession implements Session {

    private final String id;
    private final Channel channel;
    private volatile ConnectionState state;

    public DefaultSession(Channel channel) {
        this.id = UUID.randomUUID().toString();
        this.channel = channel;
        this.state = ConnectionState.CONNECTING;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void send(Message message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message);
        }
    }

    @Override
    public void disconnect() {
        state = ConnectionState.CLOSING;
        if (channel != null && channel.isActive()) {
            channel.close();
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

    public Channel getChannel() {
        return channel;
    }
}
