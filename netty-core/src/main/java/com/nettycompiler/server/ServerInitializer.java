package com.nettycompiler.server;

import com.nettycompiler.docker.DockerOrchestrator;
import com.nettycompiler.handler.RawDockerExecHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * ServerInitializer — configures the Netty pipeline for raw execution.
 * * Pipeline:
 * SocketChannel
 * -> HttpServerCodec          (HTTP codec for WebSocket upgrade)
 * -> HttpObjectAggregator     (aggregate HTTP chunks)
 * -> WebSocketServerProtocolHandler  (handles WS handshake on /ws path)
 * -> RawDockerExecHandler     (Bridges WS frames directly to docker exec)
 */
public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final DockerOrchestrator orchestrator;

    public ServerInitializer(DockerOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        // HTTP layer (for WebSocket upgrade)
        ch.pipeline().addLast("http-codec", new HttpServerCodec());
        ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65536));

        // WebSocket layer
        ch.pipeline().addLast("ws-protocol", new WebSocketServerProtocolHandler("/ws"));

        // Raw execution layer
        ch.pipeline().addLast("raw-exec-handler", new RawDockerExecHandler(
                orchestrator.getDockerClient(),
                orchestrator.getSharedContainerId()
        ));
    }
}