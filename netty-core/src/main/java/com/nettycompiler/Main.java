package com.nettycompiler;

import com.nettycompiler.handler.PythonHookBridge;
import com.nettycompiler.mc.protocol.MinecraftProtocol;
import com.nettycompiler.server.NettyServer;

/**
 * Main — boots NettyServer with MC protocol and Python bridge.
 *
 * The Python bridge needs to always be here, but the MinecraftProtocol needs to be
 * removed and this must hook onto things like an API. However, a main class must still exist
 * such that the server itself can still run. The question is am I trying to create this as a
 * server or as a API?
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        String pythonUrl = System.getenv("PYTHON_BRIDGE_URL");
        // if this is running on a server this pythonUrl shit will not work and
        // needs a hosted ip
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
