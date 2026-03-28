package com.nettycompiler.codec;

import com.nettycompiler.core.Message;
import com.nettycompiler.core.Protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.List;

/**
 * Codec between TextWebSocketFrame and Message.
 * Sits in the pipeline after Netty's WebSocket handlers.
 *
 * Inbound:  TextWebSocketFrame.text() -> Protocol.decode() -> Message
 * Outbound: Message -> Protocol.encode() -> TextWebSocketFrame
 */
public class JsonMessageCodec extends MessageToMessageCodec<TextWebSocketFrame, Message> {

    private final Protocol protocol;

    public JsonMessageCodec(Protocol protocol) {
        this.protocol = protocol;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, List<Object> out) throws Exception {
        String json = protocol.encode(msg);
        out.add(new TextWebSocketFrame(json));
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> out) throws Exception {
        Message message = protocol.decode(frame.text());
        if (message != null) {
            out.add(message);
        }
    }
}
