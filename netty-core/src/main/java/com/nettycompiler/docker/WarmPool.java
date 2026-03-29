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

    /** How long to poll for uvicorn readiness before giving up. */
    private static final int READY_TIMEOUT_SECONDS = 30;

    /** Interval between readiness poll attempts. */
    private static final long READY_POLL_MS = 250;

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
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    /**
     * Fill the pool with targetSize containers. Called once at startup.
     * Non-blocking — containers start in parallel.
     * Each container is health-checked before being added to the pool so
     * the pool only ever contains containers that are actually ready.
     */
    public void initialize() {
        System.out.println("[WarmPool] Initializing with target size " + targetSize);
        for (int i = 0; i < targetSize; i++) {
            orchestrator.startContainer()
                    .thenAcceptAsync(info -> {
                        try {
                            waitUntilReady(info, READY_TIMEOUT_SECONDS);
                        } catch (Exception e) {
                            System.err.println("[WarmPool] Container never became ready, discarding: " + e.getMessage());
                            orchestrator.destroyContainer(info.getContainerId());
                            return;
                        }
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
     * Acquire a container from the pool. Returns instantly if one is available
     * (pool containers are pre-verified ready), otherwise creates on-demand and
     * waits for uvicorn to be ready before returning.
     */
    public CompletableFuture<ContainerInfo> acquire() {
        return CompletableFuture.supplyAsync(() -> {
            // Try to get a container from the pool.
            // Pool containers have already passed a readiness check so we only
            // do a quick single-shot health ping here rather than a full retry loop.
            while (true) {
                ContainerInfo info = idleContainers.poll();
                if (info == null) break;

                try {
                    boolean healthy = orchestrator.healthCheck(info).get(5, TimeUnit.SECONDS);
                    if (healthy) {
                        return info;
                    }
                } catch (Exception e) {
                    // Stale/crashed container — discard and try the next one
                }
                orchestrator.destroyContainer(info.getContainerId());
            }

            // Pool empty — create on-demand and wait for uvicorn to be ready.
            System.out.println("[WarmPool] Pool empty, creating container on-demand");
            try {
                ContainerInfo info = orchestrator.startContainer().get(30, TimeUnit.SECONDS);
                // *** THE FIX: poll until uvicorn is actually listening ***
                waitUntilReady(info, READY_TIMEOUT_SECONDS);
                return info;
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

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Poll GET /health on the container every READY_POLL_MS milliseconds until
     * it returns HTTP 200, or until timeoutSeconds have elapsed.
     *
     * This is the core fix: a freshly-started container has the Docker process
     * running ("Status":"running") but uvicorn may take several hundred
     * milliseconds to bind to its port. A single one-shot health check races
     * against that startup window and loses. Polling solves it.
     */
    private void waitUntilReady(ContainerInfo info, int timeoutSeconds) throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        int attempt = 0;

        while (System.currentTimeMillis() < deadline) {
            attempt++;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(info.getContainerUrl() + "/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    System.out.println("[WarmPool] Container " + info.getContainerId().substring(0, 12)
                            + " ready after " + attempt + " poll(s)");
                    return;
                }
            } catch (Exception ignored) {
                // uvicorn not up yet — keep polling
            }

            Thread.sleep(READY_POLL_MS);
        }

        throw new TimeoutException("Container " + info.getContainerId().substring(0, 12)
                + " did not become ready within " + timeoutSeconds + "s");
    }

    private void replenish() {
        if (!running) return;
        int deficit = targetSize - idleContainers.size();
        if (deficit <= 0) return;

        System.out.println("[WarmPool] Replenishing " + deficit + " container(s)");
        for (int i = 0; i < deficit; i++) {
            orchestrator.startContainer()
                    .thenAcceptAsync(info -> {
                        try {
                            waitUntilReady(info, READY_TIMEOUT_SECONDS);
                        } catch (Exception e) {
                            System.err.println("[WarmPool] Replenish: container never ready, discarding: " + e.getMessage());
                            orchestrator.destroyContainer(info.getContainerId());
                            return;
                        }
                        if (!running || !idleContainers.offer(info)) {
                            orchestrator.destroyContainer(info.getContainerId());
                        } else {
                            System.out.println("[WarmPool] Replenished pool (size: " + idleContainers.size() + ")");
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