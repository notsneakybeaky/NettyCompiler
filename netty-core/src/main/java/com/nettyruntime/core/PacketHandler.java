package com.nettyruntime.core;

/**
 * PacketHandler — decide what to do with decoded packets.
 * This is the contract between Netty Core and Application Logic.
 * The PythonHookBridge implements this to forward decisions to Python.
 */
public interface PacketHandler {

    /**
     * Called when a new client connects and a Session is created.
     */
    void onConnect(Session session);

    /**
     * Called when a decoded packet arrives from either leg of the proxy.
     */
    void onPacket(Session session, Packet packet);

    /**
     * Called when a client disconnects or the session is terminated.
     */
    void onDisconnect(Session session);

    /**
     * Called on each server tick for the given session.
     */
    void onTick(Session session, int tick);
}
