package com.nettycompiler.server;

import com.nettycompiler.codec.JsonMessageCodec;
import com.nettycompiler.core.ConnectionManager;
import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.core.Protocol;
import com.nettycompiler.handler.WebSocketMessageHandler;
import com.nettycompiler.session.DefaultSession;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * ServerInitializer — configures the Netty pipeline for each new WebSocket connection.
 *
 * Pipeline:
 *   SocketChannel
 *     -> HttpServerCodec          (HTTP codec for WebSocket upgrade)
 *     -> HttpObjectAggregator     (aggregate HTTP chunks)
 *     -> HttpApiHandler           (REST endpoints: /scripts, /status — consumed before WS upgrade)
 *     -> WebSocketServerProtocolHandler  (handles WS handshake on /ws path)
 *     -> JsonMessageCodec         (TextWebSocketFrame <-> Message)
 *     -> WebSocketMessageHandler  (Message -> MessageHandler dispatch)
 *
 * Connection handling is isolated here. Business logic routing happens in MessageHandler.
 */
public class ServerInitializer extends ChannelInitializer<SocketChannel> {

    private final Protocol protocol;
    private final MessageHandler messageHandler;
    private final ConnectionManager connectionManager;

    public ServerInitializer(Protocol protocol, MessageHandler messageHandler,
                             ConnectionManager connectionManager) {
        this.protocol = protocol;
        this.messageHandler = messageHandler;
        this.connectionManager = connectionManager;
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        DefaultSession session = new DefaultSession(ch);
        connectionManager.addSession(session);

        // HTTP layer (for WebSocket upgrade + REST API)
        ch.pipeline().addLast("http-codec", new HttpServerCodec());
        ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(65536));
        ch.pipeline().addLast("http-api", new HttpApiHandler(messageHandler));

        // WebSocket layer
        ch.pipeline().addLast("ws-protocol", new WebSocketServerProtocolHandler("/ws"));

        // JSON message layer
        ch.pipeline().addLast("json-codec", new JsonMessageCodec(protocol));
        ch.pipeline().addLast("ws-handler", new WebSocketMessageHandler(session, messageHandler));

        // Cleanup session on channel close
        ch.closeFuture().addListener(future -> connectionManager.removeSession(session.getId()));
    }
}
