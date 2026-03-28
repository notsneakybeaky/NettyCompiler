package com.nettycompiler;

import com.nettycompiler.handler.PythonHookBridge;
import com.nettycompiler.jackson.JacksonFactory;
import com.nettycompiler.registry.MessageRegistry;
import com.nettycompiler.server.NettyServer;
import com.nettycompiler.ws.WebSocketProtocol;
import com.nettycompiler.ws.message.*;

/**
 * Main — boots NettyServer with WebSocket protocol and Python bridge.
 *
 * Composition root: wires registries, Jackson factory, protocol, and handler.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        String pythonUrl = System.getenv("PYTHON_BRIDGE_URL");
        if (pythonUrl == null) pythonUrl = "http://localhost:8000";

        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }

        // 1. Build registries
        MessageRegistry messageRegistry = new MessageRegistry();
        registerBuiltinMessages(messageRegistry);

        // 2. Build Jackson factory (snake_case, no domain type resolution)
        JacksonFactory jacksonFactory = new JacksonFactory();

        // 3. Build protocol
        WebSocketProtocol protocol = new WebSocketProtocol(jacksonFactory, messageRegistry);

        // 4. Build message handler (Python bridge)
        PythonHookBridge handler = new PythonHookBridge(pythonUrl, messageRegistry, jacksonFactory);

        // 5. Start server
        System.out.println("[NettyService] Starting with Python bridge at " + pythonUrl);
        NettyServer server = new NettyServer(port, protocol, handler);
        server.start();
    }

    /**
     * Register all built-in protocol message types.
     * Every message type must be registered here — unregistered types are rejected.
     */
    private static void registerBuiltinMessages(MessageRegistry registry) {
        registry.register("session_init", SessionInitMessage.class);
        registry.register("session_ack", SessionAckMessage.class);
        registry.register("error", ErrorMessage.class);
        registry.register("ping", PingMessage.class);
        registry.register("pong", PongMessage.class);
    }
}
