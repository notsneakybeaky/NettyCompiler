package com.nettycompiler.handler;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.nettycompiler.docker.DockerOrchestrator;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.nio.charset.StandardCharsets;

/**
 * Directly bridges WebSocket frames to a Docker container's stdin/stdout via docker exec.
 */
public class RawDockerExecHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final DockerClient dockerClient;
    private final String containerId;

    public RawDockerExecHandler(DockerClient dockerClient, String containerId) {
        this.dockerClient = dockerClient;
        this.containerId = containerId;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("[RawDockerExec] Client connected, bound to container: " + containerId);
        ctx.writeAndFlush(new TextWebSocketFrame("Connected to Python Sandbox."));
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String pythonCode = frame.text();

        try {
            // 1. Create the exec command to run the Python code
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("/app/venv/bin/python", "-c", pythonCode)
                    .exec();

            // 2. Start the exec instance and pipe output to the WebSocket
            dockerClient.execStartCmd(execCreate.getId())
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame item) {
                            // Wrap the stdout/stderr byte array into a WebSocket frame
                            String output = new String(item.getPayload(), StandardCharsets.UTF_8);
                            ctx.writeAndFlush(new TextWebSocketFrame(output));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            ctx.writeAndFlush(new TextWebSocketFrame("Execution Error: " + throwable.getMessage()));
                        }
                    });

        } catch (Exception e) {
            ctx.writeAndFlush(new TextWebSocketFrame("System Error: " + e.getMessage()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}