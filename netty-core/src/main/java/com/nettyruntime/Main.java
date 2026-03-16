package com.nettyruntime;

import com.nettyruntime.handler.PythonHookBridge;
import com.nettyruntime.mc.protocol.MinecraftProtocol;
import com.nettyruntime.server.NettyServer;

/**
 * Main — boots NettyServer with MC protocol and Python bridge.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        String pythonUrl = System.getenv("PYTHON_BRIDGE_URL");
        if (pythonUrl == null) pythonUrl = "http://localhost:8000";

        int port = 25565;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        MinecraftProtocol protocol = new MinecraftProtocol();
        PythonHookBridge handler = new PythonHookBridge(pythonUrl, protocol.getRegistry());

        System.out.println("[NettyRuntime] Starting with Python bridge at " + pythonUrl);
        NettyServer server = new NettyServer(port, protocol, handler);
        server.start();
    }
}
