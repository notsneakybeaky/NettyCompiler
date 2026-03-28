package com.nettycompiler.docker;

import java.time.Instant;

/**
 * Tracks a Docker container's identity, URL, and assignment state.
 * Mutable fields are volatile for cross-thread visibility.
 */
public class ContainerInfo {

    public enum ContainerState { STARTING, IDLE, ASSIGNED, STOPPING }

    private final String containerId;
    private final String containerUrl;
    private final Instant createdAt;
    private volatile String sessionId;
    private volatile ContainerState state;
    private volatile Instant lastActivityAt;

    public ContainerInfo(String containerId, String containerUrl) {
        this.containerId = containerId;
        this.containerUrl = containerUrl;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.state = ContainerState.STARTING;
    }

    public String getContainerId() { return containerId; }
    public String getContainerUrl() { return containerUrl; }
    public Instant getCreatedAt() { return createdAt; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public ContainerState getState() { return state; }
    public void setState(ContainerState state) { this.state = state; }

    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
}
