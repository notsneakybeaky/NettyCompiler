package com.nettycompiler.server;

import com.nettycompiler.core.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * NettyServer — the entry point. Boots Netty with injected Protocol and PacketHandler.
 * Formerly AiGatewayServer. Knows nothing about MC, Python, or application logic.
 */
public class NettyServer {

    private final int port;
    private final Protocol protocol;
    private final PacketHandler packetHandler;
    private final ConnectionManager connectionManager;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(int port, Protocol protocol, PacketHandler packetHandler) {
        this.port = port;
        this.protocol = protocol;
        this.packetHandler = packetHandler;
        this.connectionManager = new ConnectionManager();
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(protocol, packetHandler, connectionManager));

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("[NettyServer] Listening on port " + port);
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
