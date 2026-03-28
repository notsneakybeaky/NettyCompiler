package com.nettycompiler.docker;

import com.nettycompiler.core.ConnectionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * IdleReaper — periodically scans assigned containers and disconnects
 * sessions that have been idle beyond the configured timeout.
 *
 * The disconnect triggers ContainerSessionHandler.onDisconnect,
 * which releases the container back to the warm pool.
 */
public class IdleReaper {

    private final ContainerSessionHandler handler;
    private final ConnectionManager connectionManager;
    private final Duration idleTimeout;
    private final ScheduledExecutorService scheduler;

    public IdleReaper(ContainerSessionHandler handler, ConnectionManager connectionManager,
                      Duration idleTimeout) {
        this.handler = handler;
        this.connectionManager = connectionManager;
        this.idleTimeout = idleTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "idle-reaper");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::reap, 60, 60, TimeUnit.SECONDS);
        System.out.println("[IdleReaper] Started with " + idleTimeout.toMinutes() + "min timeout");
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void reap() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        List<String> toDisconnect = new ArrayList<>();

        for (String sessionId : handler.getAssignedSessionIds()) {
            ContainerInfo info = handler.getContainerForSession(sessionId);
            if (info != null && info.getLastActivityAt().isBefore(cutoff)) {
                toDisconnect.add(sessionId);
            }
        }

        for (String sessionId : toDisconnect) {
            var session = connectionManager.getSession(sessionId);
            if (session != null) {
                System.out.println("[IdleReaper] Disconnecting idle session " + sessionId);
                session.disconnect();
            }
        }
    }
}
