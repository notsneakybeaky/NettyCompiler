package com.nettycompiler.docker;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * WarmPool — maintains a pool of pre-started, idle Python containers
 * for instant assignment when a user connects.
 *
 * If the pool is empty, falls back to on-demand container creation.
 * A background task replenishes the pool every 5 seconds.
 */
public class WarmPool {

    private final DockerOrchestrator orchestrator;
    private final int targetSize;
    private final LinkedBlockingQueue<ContainerInfo> idleContainers;
    private final ScheduledExecutorService replenisher;
    private final HttpClient http;
    private volatile boolean running = true;

    public WarmPool(DockerOrchestrator orchestrator, int targetSize) {
        this.orchestrator = orchestrator;
        this.targetSize = targetSize;
        this.idleContainers = new LinkedBlockingQueue<>(targetSize * 2);
        this.replenisher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "warm-pool-replenisher");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Fill the pool with targetSize containers. Called once at startup.
     * Non-blocking — containers start in parallel.
     */
    public void initialize() {
        System.out.println("[WarmPool] Initializing with target size " + targetSize);
        for (int i = 0; i < targetSize; i++) {
            orchestrator.startContainer()
                    .thenAccept(info -> {
                        if (!idleContainers.offer(info)) {
                            orchestrator.destroyContainer(info.getContainerId());
                        } else {
                            System.out.println("[WarmPool] Added container to pool (size: " + idleContainers.size() + ")");
                        }
                    })
                    .exceptionally(err -> {
                        System.err.println("[WarmPool] Failed to start warm container: " + err.getMessage());
                        return null;
                    });
        }

        // Start background replenishment
        replenisher.scheduleAtFixedRate(this::replenish, 10, 5, TimeUnit.SECONDS);
    }

    /**
     * Acquire a container from the pool. Returns instantly if one is available,
     * otherwise creates on-demand (slower).
     */
    public CompletableFuture<ContainerInfo> acquire() {
        return CompletableFuture.supplyAsync(() -> {
            // Try to get a healthy container from the pool
            while (true) {
                ContainerInfo info = idleContainers.poll();
                if (info == null) break;

                // Health check before handing out
                try {
                    boolean healthy = orchestrator.healthCheck(info).get(5, TimeUnit.SECONDS);
                    if (healthy) {
                        return info;
                    }
                } catch (Exception e) {
                    // Unhealthy — discard
                }
                orchestrator.destroyContainer(info.getContainerId());
            }

            // Pool empty — create on-demand
            System.out.println("[WarmPool] Pool empty, creating container on-demand");
            try {
                return orchestrator.startContainer().get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    /**
     * Release a container back to the pool after a session ends.
     * Resets the container's state before returning it.
     */
    public void release(ContainerInfo info) {
        CompletableFuture.runAsync(() -> {
            try {
                // Reset container state for reuse
                resetContainer(info);
                info.setSessionId(null);
                info.setState(ContainerInfo.ContainerState.IDLE);

                if (!idleContainers.offer(info)) {
                    // Pool is full — destroy the excess container
                    orchestrator.destroyContainer(info.getContainerId());
                } else {
                    System.out.println("[WarmPool] Released container back to pool (size: " + idleContainers.size() + ")");
                }
            } catch (Exception e) {
                System.err.println("[WarmPool] Failed to release container, destroying: " + e.getMessage());
                orchestrator.destroyContainer(info.getContainerId());
            }
        });
    }

    /**
     * Shut down the pool: stop replenishment and destroy all idle containers.
     */
    public void shutdown() {
        running = false;
        replenisher.shutdown();
        ContainerInfo info;
        while ((info = idleContainers.poll()) != null) {
            orchestrator.destroyContainer(info.getContainerId());
        }
        System.out.println("[WarmPool] Shut down");
    }

    public int getIdleCount() {
        return idleContainers.size();
    }

    private void replenish() {
        if (!running) return;
        int deficit = targetSize - idleContainers.size();
        if (deficit <= 0) return;

        System.out.println("[WarmPool] Replenishing " + deficit + " container(s)");
        for (int i = 0; i < deficit; i++) {
            orchestrator.startContainer()
                    .thenAccept(info -> {
                        if (!running || !idleContainers.offer(info)) {
                            orchestrator.destroyContainer(info.getContainerId());
                        }
                    })
                    .exceptionally(err -> {
                        System.err.println("[WarmPool] Replenish failed: " + err.getMessage());
                        return null;
                    });
        }
    }

    /**
     * POST /reset to clear scripts and state in the container.
     */
    private void resetContainer(ContainerInfo info) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(info.getContainerUrl() + "/reset"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .timeout(Duration.ofSeconds(5))
                .build();
        http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
