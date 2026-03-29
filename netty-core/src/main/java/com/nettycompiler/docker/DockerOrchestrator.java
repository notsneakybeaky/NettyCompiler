package com.nettycompiler.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * DockerOrchestrator — creates, starts, health-checks, and destroys
 * per-user Python worker containers via the Docker API.
 *
 * All operations return CompletableFuture and execute on a dedicated thread pool
 * to never block the Netty event loop.
 */
public class DockerOrchestrator {

    private final DockerClient docker;
    private final String imageName;
    private final String networkName;
    private final ExecutorService executor;
    private final HttpClient http;
    private final ConcurrentHashMap<String, ContainerInfo> containers = new ConcurrentHashMap<>();

    private static final int HEALTH_CHECK_RETRIES = 10;
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofMillis(500);

    public DockerOrchestrator(String imageName, String networkName) {
        this.imageName = imageName;
        this.networkName = networkName;
        this.executor = Executors.newFixedThreadPool(4);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

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

    /**
     * Create + start a container, wait for it to pass health check, return ContainerInfo.
     */
    // 1. Add this counter at the top of the class to give each container a unique port
    private final java.util.concurrent.atomic.AtomicInteger portCounter = new java.util.concurrent.atomic.AtomicInteger(8001);

    public CompletableFuture<ContainerInfo> startContainer() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String name = "ncs-worker-" + UUID.randomUUID().toString().substring(0, 8);
                com.github.dockerjava.api.model.ExposedPort tcp8000 = com.github.dockerjava.api.model.ExposedPort.tcp(8000);

                // 1. Map container port 8000 to a RANDOM available port on your host (0.0.0.0:0)
                com.github.dockerjava.api.command.CreateContainerResponse created = docker.createContainerCmd(imageName)
                        .withName(name)
                        .withExposedPorts(tcp8000)
                        .withHostConfig(com.github.dockerjava.api.model.HostConfig.newHostConfig()
                                .withNetworkMode(networkName)
                                .withPortBindings(com.github.dockerjava.api.model.PortBinding.parse("0.0.0.0:0:8000")))
                        .exec();

                String containerId = created.getId();
                docker.startContainerCmd(containerId).exec();

                // 2. Ask Docker which port it actually assigned to this container
                com.github.dockerjava.api.command.InspectContainerResponse inspect = docker.inspectContainerCmd(containerId).exec();
                var bindings = inspect.getNetworkSettings().getPorts().getBindings().get(tcp8000);
                String hostPort = bindings[0].getHostPortSpec();

                // 3. Now Java can reach the worker via localhost:PORT
                String containerUrl = "http://localhost:" + hostPort;

                ContainerInfo info = new ContainerInfo(containerId, containerUrl);
                info.setState(ContainerInfo.ContainerState.IDLE);
                containers.put(containerId, info);

                System.out.println("[DockerOrchestrator] Worker " + name + " is live at " + containerUrl);
                return info;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * Stop + remove a container.
     */
    public CompletableFuture<Void> destroyContainer(String containerId) {
        return CompletableFuture.runAsync(() -> {
            try {
                containers.remove(containerId);
                docker.stopContainerCmd(containerId).withTimeout(5).exec();
                docker.removeContainerCmd(containerId).withForce(true).exec();
                System.out.println("[DockerOrchestrator] Destroyed container " + containerId);
            } catch (Exception e) {
                System.err.println("[DockerOrchestrator] Failed to destroy " + containerId + ": " + e.getMessage());
            }
        }, executor);
    }

    /**
     * Health-check a container by hitting GET /health.
     */
    public CompletableFuture<Boolean> healthCheck(ContainerInfo info) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(info.getContainerUrl() + "/health"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }, executor);
    }

    /**
     * Check that the worker image exists locally.
     */
    public CompletableFuture<Void> ensureImageExists() {
        return CompletableFuture.runAsync(() -> {
            try {
                docker.inspectImageCmd(imageName).exec();
                System.out.println("[DockerOrchestrator] Image '" + imageName + "' found");
            } catch (Exception e) {
                throw new CompletionException(
                        new RuntimeException("Docker image '" + imageName + "' not found. "
                                + "Build it with: docker build -t " + imageName + " ./python-worker", e));
            }
        }, executor);
    }

    /**
     * Destroy all tracked containers. Called on shutdown.
     */
    public CompletableFuture<Void> destroyAll() {
        CompletableFuture<?>[] futures = containers.keySet().stream()
                .map(this::destroyContainer)
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    /**
     * Shut down the executor. Call after destroyAll completes.
     */
    public void close() {
        executor.shutdown();
    }

    /**
     * Ensure the Docker network exists, creating it if necessary.
     */
    public CompletableFuture<Void> ensureNetworkExists() {
        return CompletableFuture.runAsync(() -> {
            try {
                boolean exists = docker.listNetworksCmd()
                        .withNameFilter(networkName)
                        .exec()
                        .stream()
                        .anyMatch(n -> n.getName().equals(networkName));
                if (!exists) {
                    docker.createNetworkCmd()
                            .withName(networkName)
                            .withDriver("bridge")
                            .exec();
                    System.out.println("[DockerOrchestrator] Created network '" + networkName + "'");
                } else {
                    System.out.println("[DockerOrchestrator] Network '" + networkName + "' exists");
                }
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private void waitForHealth(String containerUrl) throws Exception {
        for (int i = 0; i < HEALTH_CHECK_RETRIES; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(containerUrl + "/health"))
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) return;
            } catch (Exception ignored) {
                // Container still starting
            }
            Thread.sleep(HEALTH_CHECK_INTERVAL.toMillis());
        }
        throw new RuntimeException("Container at " + containerUrl + " failed health check after "
                + HEALTH_CHECK_RETRIES + " retries");
    }
}