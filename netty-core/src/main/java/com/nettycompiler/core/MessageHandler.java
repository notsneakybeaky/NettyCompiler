package com.nettycompiler.core;

/**
 * MessageHandler — decide what to do with decoded messages.
 * This is the contract between Netty Core and Application Logic.
 * The PythonHookBridge implements this to forward decisions to Python containers.
 */
public interface MessageHandler {

    /**
     * Called when a new client connects and a Session is created.
     */
    void onConnect(Session session);

    /**
     * Called when a decoded message arrives from the client.
     */
    void onMessage(Session session, Message message);

    /**
     * Called when a client disconnects or the session is terminated.
     */
    void onDisconnect(Session session);
}
