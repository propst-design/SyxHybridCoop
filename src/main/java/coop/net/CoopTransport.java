package coop.net;

import java.io.Closeable;
import java.io.IOException;

/**
 * Common networking abstraction for Syx Hybrid Co-Op.
 * Supports both raw TCP socket channels (for local dual-instance testing and LAN)
 * and Steam P2P networking (for Steam multiplayer).
 */
public interface CoopTransport extends Closeable {

    /**
     * Start hosting on the specified port (TCP) or create a P2P lobby (Steam).
     */
    void startHost(int port) throws IOException;

    /**
     * Connect to a remote host (TCP) or join a lobby (Steam).
     */
    void connect(String address, int port) throws IOException;

    /**
     * Send reliable, ordered packet data to connected peer(s).
     */
    void sendReliable(byte[] data) throws IOException;

    /**
     * Send unreliable packet data (such as remote cursor coordinates).
     */
    void sendUnreliable(byte[] data) throws IOException;

    /**
     * Poll the next incoming packet from the receive queue.
     * Non-blocking; returns null if no packet is currently available.
     */
    byte[] pollIncoming();

    /**
     * Check if transport is currently connected to at least one peer.
     */
    boolean isConnected();

    /**
     * Check if this instance is the session host.
     */
    boolean isHost();

    /**
     * Frame update hook for pumping networking callbacks (e.g. SteamAPI.runCallbacks()).
     */
    void update();

    /**
     * Close the transport, disconnect peers, and release all socket/network resources.
     */
    @Override
    void close();
}
