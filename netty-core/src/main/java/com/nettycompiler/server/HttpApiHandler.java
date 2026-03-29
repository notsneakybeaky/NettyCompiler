package com.nettycompiler.server;

import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.docker.ContainerInfo;
import com.nettycompiler.docker.ContainerSessionHandler;
import com.nettycompiler.handler.PythonHookBridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private final HttpClient http = HttpClient.newHttpClient();

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
        } else if (method == HttpMethod.POST && uri.startsWith("/sessions/") && uri.endsWith("/hot-flash")) {
            String sessionId = uri.split("/")[2];
            handleHotFlash(ctx, request, sessionId);
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

            PythonHookBridge bridge = findBridge();
            if (bridge != null) {
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

    private void handleHotFlash(ChannelHandlerContext ctx, FullHttpRequest request, String sessionId) {
        try {
            ContainerSessionHandler containerHandler = findContainerHandler();
            ContainerInfo info = containerHandler.getContainerForSession(sessionId);
            if (info == null) { sendJson(ctx, 404, Map.of("error", "no session")); return; }

            // THE FIX: Copy the buffer bytes without consuming the index
            byte[] bodyBytes = new byte[request.content().readableBytes()];
            request.content().getBytes(request.content().readerIndex(), bodyBytes);
            String body = new String(bodyBytes, io.netty.util.CharsetUtil.UTF_8);

            java.net.http.HttpRequest hotFlashReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(info.getContainerUrl() + "/hot-flash"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();

            http.sendAsync(hotFlashReq, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> sendJson(ctx, resp.statusCode(), Map.of("worker_status", resp.statusCode())));
        } catch (Exception e) {
            sendJson(ctx, 400, Map.of("error", e.getMessage()));
        }
    }

    private PythonHookBridge findBridge() {
        if (messageHandler instanceof PythonHookBridge bridge) return bridge;
        if (messageHandler instanceof ContainerSessionHandler csh
                && csh.getDelegate() instanceof PythonHookBridge bridge) return bridge;
        return null;
    }

    private ContainerSessionHandler findContainerHandler() {
        if (messageHandler instanceof ContainerSessionHandler csh) return csh;
        return null;
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
