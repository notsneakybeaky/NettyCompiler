package com.nettycompiler.docker;

import com.nettycompiler.core.Session;

/**
 * Callback interface for ContainerSessionHandler to send protocol-level
 * messages without depending on protocol-ws message types.
 *
 * Implemented in Main.java where both netty-core and protocol-ws are available.
 */
public interface SessionNotifier {

    /**
     * Notify the client that their session is initializing (container provisioning).
     */
    void sendInitializing(Session session);

    /**
     * Notify the client that their session is now active (container ready).
     */
    void sendActive(Session session);

    /**
     * Notify the client of a container allocation failure.
     */
    void sendError(Session session, String code, String detail);
}
