package com.nettycompiler.server;

import com.nettycompiler.codec.PacketCodec;
import com.nettycompiler.core.*;
import com.nettycompiler.handler.ProtocolHandler;
import com.nettycompiler.session.DefaultSession;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * ServerInitializer — configures the Netty pipeline for each new connection.
 * Injects Protocol and PacketHandler via constructor. Knows nothing about
 * what protocol or application logic is being used.
 */
public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Protocol protocol;
    private final PacketHandler packetHandler;
    private final ConnectionManager connectionManager;

    public ServerInitializer(Protocol protocol, PacketHandler packetHandler,
                             ConnectionManager connectionManager) {
        this.protocol = protocol;
        this.packetHandler = packetHandler;
        this.connectionManager = connectionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        DefaultSession session = new DefaultSession(ch, protocol);
        connectionManager.addSession(session);

        ch.pipeline().addLast("codec", new PacketCodec(protocol, PacketDirection.SERVERBOUND, session));
        ch.pipeline().addLast("handler", new ProtocolHandler(session, packetHandler, protocol));

        // Remove session on channel close
        ch.closeFuture().addListener(future -> connectionManager.removeSession(session.getId()));
    }
}
