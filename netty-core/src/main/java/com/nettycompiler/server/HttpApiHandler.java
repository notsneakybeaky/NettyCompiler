package com.nettycompiler.server;

import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.handler.PythonHookBridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.Map;

/**
 * HttpApiHandler — REST API for script management and server status.
 * Routes:
 *   POST /scripts   — upload a script (forwards to Python worker)
 *   GET  /status    — server health check
 *
 * Non-matching requests are passed through to the WebSocket handler.
 */
public class HttpApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final MessageHandler messageHandler;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpApiHandler(MessageHandler messageHandler) {
        super(false); // Don't auto-release — we may pass through
        this.messageHandler = messageHandler;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        HttpMethod method = request.method();

        if (method == HttpMethod.POST && "/scripts".equals(uri)) {
            handleScriptUpload(ctx, request);
        } else if (method == HttpMethod.GET && "/status".equals(uri)) {
            sendJson(ctx, 200, Map.of("status", "running"));
        } else {
            // Pass through to WebSocket handler
            ctx.fireChannelRead(request.retain());
        }
    }

    private void handleScriptUpload(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(CharsetUtil.UTF_8);
            JsonNode payload = mapper.readTree(body);
            String scriptId = payload.get("script_id").asText();
            String source = payload.get("source").asText();

            if (messageHandler instanceof PythonHookBridge bridge) {
                bridge.loadScript(scriptId, source)
                        .thenAccept(result -> sendJson(ctx, 200,
                            Map.of("status", "loaded", "script_id", scriptId)))
                        .exceptionally(err -> {
                            sendJson(ctx, 500, Map.of("error", err.getMessage()));
                            return null;
                        });
            } else {
                sendJson(ctx, 501, Map.of("error", "script loading not supported"));
            }
        } catch (Exception e) {
            sendJson(ctx, 400, Map.of("error", e.getMessage()));
        }
    }

    private void sendJson(ChannelHandlerContext ctx, int status, Map<String, Object> data) {
        try {
            String json = mapper.writeValueAsString(data);
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(status),
                    Unpooled.copiedBuffer(json, CharsetUtil.UTF_8));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        } catch (Exception e) {
            ctx.close();
        }
    }
}
