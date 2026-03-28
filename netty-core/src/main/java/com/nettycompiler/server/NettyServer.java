package com.nettycompiler.server;

import com.nettycompiler.core.ConnectionManager;
import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.core.Protocol;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * NettyServer — the entry point. Boots Netty with injected Protocol and MessageHandler.
 * Knows nothing about specific protocols, Python, or application logic.
 */
public class NettyServer {

    private final int port;
    private final Protocol protocol;
    private final MessageHandler messageHandler;
    private final ConnectionManager connectionManager;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(int port, Protocol protocol, MessageHandler messageHandler) {
        this.port = port;
        this.protocol = protocol;
        this.messageHandler = messageHandler;
        this.connectionManager = new ConnectionManager();
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(protocol, messageHandler, connectionManager));

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("[NettyServer] Listening on port " + port);
            System.out.println("[NettyServer] WebSocket endpoint: ws://localhost:" + port + "/ws");
            System.out.println("[NettyServer] REST API: http://localhost:" + port + "/status");
            future.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
}
