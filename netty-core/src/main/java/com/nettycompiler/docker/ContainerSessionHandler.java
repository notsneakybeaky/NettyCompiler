package com.nettycompiler.docker;

import com.nettycompiler.core.ConnectionState;
import com.nettycompiler.core.Message;
import com.nettycompiler.core.MessageHandler;
import com.nettycompiler.core.Session;
import com.nettycompiler.handler.PythonHookBridge;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ContainerSessionHandler — MessageHandler decorator that manages Docker container
 * lifecycle around the existing PythonHookBridge.
 *
 * On connect:  acquires a container from the warm pool (async, non-blocking),
 *              binds it to the session, then delegates to PythonHookBridge.
 * On message:  updates activity timestamp, delegates.
 * On disconnect: delegates, then releases the container back to the pool.
 */
public class ContainerSessionHandler implements MessageHandler {

    private final MessageHandler delegate;
    private final WarmPool warmPool;
    private final SessionNotifier notifier;
    private final ConcurrentHashMap<String, ContainerInfo> sessionContainers = new ConcurrentHashMap<>();

    public ContainerSessionHandler(MessageHandler delegate, WarmPool warmPool, SessionNotifier notifier) {
        this.delegate = delegate;
        this.warmPool = warmPool;
        this.notifier = notifier;
    }

    @Override
    public void onConnect(Session session) {
        // Immediate ack — container is being provisioned
        session.setState(ConnectionState.INITIALIZING);
        notifier.sendInitializing(session);

        // Acquire container asynchronously — never blocks the event loop
        warmPool.acquire().thenAccept(container -> {
            container.setSessionId(session.getId());
            container.setState(ContainerInfo.ContainerState.ASSIGNED);
            container.setLastActivityAt(Instant.now());
            sessionContainers.put(session.getId(), container);

            // Bind session to container URL in PythonHookBridge
            if (delegate instanceof PythonHookBridge bridge) {
                bridge.setSessionUrl(session.getId(), container.getContainerUrl());
            }

            // Transition to active
            session.setState(ConnectionState.ACTIVE);
            notifier.sendActive(session);

            // Now forward the connect hook to the delegate
            delegate.onConnect(session);

            System.out.println("[ContainerSessionHandler] Session " + session.getId()
                    + " -> container " + container.getContainerId());
        }).exceptionally(err -> {
            System.err.println("[ContainerSessionHandler] Failed to acquire container for "
                    + session.getId() + ": " + err.getMessage());
            notifier.sendError(session, "container_failed", "Could not allocate a worker container");
            session.disconnect();
            return null;
        });
    }

    @Override
    public void onMessage(Session session, Message message) {
        // Update activity timestamp for idle timeout tracking
        ContainerInfo info = sessionContainers.get(session.getId());
        if (info != null) {
            info.setLastActivityAt(Instant.now());
        }
        delegate.onMessage(session, message);
    }

    @Override
    public void onDisconnect(Session session) {
        // Forward disconnect to delegate first (so Python gets the hook)
        delegate.onDisconnect(session);

        // Release container back to pool
        ContainerInfo info = sessionContainers.remove(session.getId());
        if (info != null) {
            warmPool.release(info);
            System.out.println("[ContainerSessionHandler] Released container for session " + session.getId());
        }
    }

    /**
     * Get the delegate handler (for HttpApiHandler to access PythonHookBridge).
     */
    public MessageHandler getDelegate() {
        return delegate;
    }

    /**
     * Get container info for a session (used by IdleReaper and hot-flash endpoint).
     */
    public ContainerInfo getContainerForSession(String sessionId) {
        return sessionContainers.get(sessionId);
    }

    /**
     * All currently assigned session IDs (used by IdleReaper).
     */
    public Collection<String> getAssignedSessionIds() {
        return sessionContainers.keySet();
    }
}
