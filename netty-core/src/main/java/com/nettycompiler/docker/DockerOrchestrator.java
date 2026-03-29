package com.nettycompiler.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DockerOrchestrator — manages a single, shared Python sandbox container.
 * No HTTP routing, no port exposure. Just a raw execution environment.
 */
public class DockerOrchestrator {

    private final DockerClient docker;
    private final String imageName;
    private final ExecutorService executor;
    private String sharedContainerId;

    public DockerOrchestrator(String imageName) {
        this.imageName = imageName;
        this.executor = Executors.newSingleThreadExecutor();

        var config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.docker = DockerClientImpl.getInstance(config, httpClient);
    }

    public DockerClient getDockerClient() {
        return docker;
    }

    public String getSharedContainerId() {
        return sharedContainerId;
    }

    /**
     * Creates and starts the single shared sandbox container.
     */
    public CompletableFuture<Void> startSharedSandbox() {
        return CompletableFuture.runAsync(() -> {
            try {
                String name = "netty-python-sandbox";

                // Clean up any old container with the same name
                try {
                    docker.removeContainerCmd(name).withForce(true).exec();
                } catch (Exception ignored) {}

                CreateContainerResponse created = docker.createContainerCmd(imageName)
                        .withName(name)
                        // No ports needed! All communication is via docker exec
                        .exec();

                sharedContainerId = created.getId();
                docker.startContainerCmd(sharedContainerId).exec();

                System.out.println("[DockerOrchestrator] Shared Sandbox live: " + sharedContainerId.substring(0, 12));
            } catch (Exception e) {
                throw new RuntimeException("Failed to start shared sandbox", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> ensureImageExists() {
        return CompletableFuture.runAsync(() -> {
            try {
                docker.inspectImageCmd(imageName).exec();
                System.out.println("[DockerOrchestrator] Image '" + imageName + "' found");
            } catch (Exception e) {
                throw new RuntimeException("Docker image '" + imageName + "' not found. "
                        + "Build it with: docker build -t " + imageName + " ./python-worker", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> destroyAll() {
        return CompletableFuture.runAsync(() -> {
            if (sharedContainerId != null) {
                try {
                    docker.stopContainerCmd(sharedContainerId).withTimeout(5).exec();
                    docker.removeContainerCmd(sharedContainerId).withForce(true).exec();
                    System.out.println("[DockerOrchestrator] Destroyed shared sandbox");
                } catch (Exception e) {
                    System.err.println("[DockerOrchestrator] Failed to destroy sandbox: " + e.getMessage());
                }
            }
        }, executor);
    }
}