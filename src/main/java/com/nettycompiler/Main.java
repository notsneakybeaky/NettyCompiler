package com.nettycompiler;

import com.nettycompiler.docker.DockerOrchestrator;
import com.nettycompiler.server.NettyServer;

/**
 * Main — boots NettyServer with the raw Docker execution engine.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        int port = 8080;
        String dockerImage = System.getenv().getOrDefault("DOCKER_WORKER_IMAGE", "netty-python-worker");

        // 1. Initialize simple Docker Orchestrator
        DockerOrchestrator orchestrator = new DockerOrchestrator(dockerImage);

        // Ensure image exists and start the shared dormant sandbox
        orchestrator.ensureImageExists().join();
        orchestrator.startSharedSandbox().join();

        // 2. Boot Netty Server
        NettyServer server = new NettyServer(port, orchestrator);

        // 3. Graceful Shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[NettyService] Shutting down...");
            orchestrator.destroyAll().join();
            server.shutdown();
        }));

        System.out.println("[NettyService] Starting on port " + port);
        server.start();
    }
}