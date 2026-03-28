package com.nettycompiler.server;

import com.nettycompiler.handler.PythonHookBridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.Map;

/**
 * HttpApiHandler — REST API for script management and server status.
 * Routes:
 *   POST /scripts   — upload a script (forwards to Python worker)
 *   GET  /status    — server health check
 */
public class HttpApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final PythonHookBridge pythonBridge;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpApiHandler(PythonHookBridge pythonBridge) {
        this.pythonBridge = pythonBridge;
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
            sendJson(ctx, 404, Map.of("error", "not found"));
        }
    }

    private void handleScriptUpload(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String body = request.content().toString(CharsetUtil.UTF_8);
            JsonNode payload = mapper.readTree(body);
            String scriptId = payload.get("scriptId").asText();
            String source = payload.get("source").asText();

            pythonBridge.loadScript(scriptId, source)
                    .thenAccept(result -> sendJson(ctx, 200, Map.of("status", "loaded", "scriptId", scriptId)))
                    .exceptionally(err -> {
                        sendJson(ctx, 500, Map.of("error", err.getMessage()));
                        return null;
                    });
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
