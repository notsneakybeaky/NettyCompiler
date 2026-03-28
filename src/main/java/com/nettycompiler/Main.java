package com.nettycompiler;

import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.docker.ContainerSessionHandler;
import com.nettycompiler.docker.DockerOrchestrator;
import com.nettycompiler.docker.IdleReaper;
import com.nettycompiler.docker.SessionNotifier;
import com.nettycompiler.docker.WarmPool;
import com.nettycompiler.handler.PythonHookBridge;
import com.nettycompiler.jackson.JacksonFactory;
import com.nettycompiler.registry.MessageRegistry;
import com.nettycompiler.server.NettyServer;
import com.nettycompiler.ws.WebSocketProtocol;
import com.nettycompiler.ws.message.*;

import java.time.Duration;

/**
 * Main — boots NettyServer with WebSocket protocol and Python bridge.
 *
 * Composition root: wires registries, Jackson factory, protocol, handler,
 * and Docker container orchestration (when DOCKER_WORKER_IMAGE is set).
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        String pythonUrl = env("PYTHON_BRIDGE_URL", "http://localhost:8000");
        String dockerImage = env("DOCKER_WORKER_IMAGE", null);
        String dockerNetwork = env("DOCKER_NETWORK", "netty-bridge");
        int warmPoolSize = intEnv("WARM_POOL_SIZE", 5);
        Duration idleTimeout = Duration.ofMinutes(longEnv("IDLE_TIMEOUT_MINUTES", 30));

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
        PythonHookBridge bridge = new PythonHookBridge(pythonUrl, messageRegistry, jacksonFactory);

        // 5. Docker container orchestration (if configured)
        MessageHandler handler;
        DockerOrchestrator orchestrator = null;
        WarmPool warmPool = null;
        IdleReaper reaper = null;

        if (dockerImage != null) {
            System.out.println("[NettyService] Docker orchestration enabled — image: " + dockerImage);
            orchestrator = new DockerOrchestrator(dockerImage, dockerNetwork);

            // Ensure network and image exist (OK to block at startup)
            orchestrator.ensureNetworkExists().join();
            orchestrator.ensureImageExists().join();

            warmPool = new WarmPool(orchestrator, warmPoolSize);
            warmPool.initialize();

            SessionNotifier notifier = new SessionNotifier() {
                @Override public void sendInitializing(com.nettycompiler.core.Session session) {
                    session.send(new SessionAckMessage(session.getId(), "initializing"));
                }
                @Override public void sendActive(com.nettycompiler.core.Session session) {
                    session.send(new SessionAckMessage(session.getId(), "active"));
                }
                @Override public void sendError(com.nettycompiler.core.Session session, String code, String detail) {
                    session.send(new ErrorMessage(code, detail));
                }
            };
            handler = new ContainerSessionHandler(bridge, warmPool, notifier);
        } else {
            System.out.println("[NettyService] Docker orchestration disabled — using shared worker at " + pythonUrl);
            handler = bridge;
        }

        // 6. Start server
        NettyServer server = new NettyServer(port, protocol, handler);

        // 7. Idle reaper (only with Docker orchestration)
        if (handler instanceof ContainerSessionHandler containerHandler) {
            reaper = new IdleReaper(containerHandler, server.getConnectionManager(), idleTimeout);
            reaper.start();
        }

        // 8. Shutdown hook
        final DockerOrchestrator orch = orchestrator;
        final WarmPool pool = warmPool;
        final IdleReaper rpr = reaper;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[NettyService] Shutting down...");
            if (rpr != null) rpr.shutdown();
            if (pool != null) pool.shutdown();
            if (orch != null) {
                orch.destroyAll().join();
                orch.close();
            }
            server.shutdown();
        }));

        System.out.println("[NettyService] Starting on port " + port);
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

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return val != null ? val : defaultValue;
    }

    private static int intEnv(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static long longEnv(String key, long defaultValue) {
        String val = System.getenv(key);
        if (val == null) return defaultValue;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return defaultValue; }
    }
}
