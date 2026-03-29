package com.nettycompiler.server;

import com.nettycompiler.docker.DockerOrchestrator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * NettyServer — boots Netty with the raw Docker execution engine.
 */
public class NettyServer {

    private final int port;
    private final DockerOrchestrator orchestrator;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(int port, DockerOrchestrator orchestrator) {
        this.port = port;
        this.orchestrator = orchestrator;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ServerInitializer(orchestrator));

            ChannelFuture future = bootstrap.bind(port).sync();
            System.out.println("[NettyServer] Listening on port " + port);
            System.out.println("[NettyServer] WebSocket endpoint: ws://localhost:" + port + "/ws");
            future.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
    }
}