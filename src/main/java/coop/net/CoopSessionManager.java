package coop.net;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.codedisaster.steamworks.SteamAPI;

/**
 * Manages the multiplayer session lifecycle, active transport backend,
 * role assignment (Host vs Client), and coordination between save streaming
 * and lockstep command queues.
 */
public class CoopSessionManager {

    public enum Role {
        NONE,
        HOST,
        CLIENT
    }

    public enum SessionMode {
        MODE_A_SHARED_CITY,
        MODE_B_SHARED_WORLD
    }

    public enum SessionState {
        IDLE,
        LOBBY,
        HANDSHAKE_SYNC,
        ACTIVE_SIM,
        BATTLE_SUBSTATE
    }

    private static final CoopSessionManager INSTANCE = new CoopSessionManager();

    private volatile Role role = Role.NONE;
    private volatile SessionMode sessionMode = SessionMode.MODE_A_SHARED_CITY;
    private volatile SessionState sessionState = SessionState.IDLE;

    private CoopTransport transport;
    private SaveStreamer.Receiver saveReceiver;
    private final Queue<byte[]> commandQueue = new ConcurrentLinkedQueue<byte[]>();

    private CoopSessionManager() {}

    public static CoopSessionManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes network roles and transports from system properties or Steam environment.
     * Evaluates -Dcoop.testRole=HOST/CLIENT, -Dcoop.port=27015, -Dcoop.connect=127.0.0.1:27015.
     */
    public synchronized void initFromSystemProperties() {
        String testRole = System.getProperty("coop.testRole");

        if ("HOST".equalsIgnoreCase(testRole)) {
            int port = 27015;
            String portProp = System.getProperty("coop.port");
            if (portProp != null && !portProp.isEmpty()) {
                try {
                    port = Integer.parseInt(portProp);
                } catch (NumberFormatException ignored) {}
            }
            try {
                TcpSocketTransport tcp = new TcpSocketTransport();
                tcp.startHost(port);
                this.transport = tcp;
                this.role = Role.HOST;
                this.sessionState = SessionState.LOBBY;
                System.out.println("[CoopSessionManager] Initialized as HOST on TCP port " + port);
            } catch (IOException e) {
                System.err.println("[CoopSessionManager] Failed to start TCP host on port " + port + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else if ("CLIENT".equalsIgnoreCase(testRole)) {
            String connectStr = System.getProperty("coop.connect", "127.0.0.1:27015");
            String host = "127.0.0.1";
            int port = 27015;
            if (connectStr.contains(":")) {
                String[] parts = connectStr.split(":", 2);
                host = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {}
            } else {
                host = connectStr;
            }

            try {
                TcpSocketTransport tcp = new TcpSocketTransport();
                tcp.connect(host, port);
                this.transport = tcp;
                this.role = Role.CLIENT;
                this.sessionState = SessionState.HANDSHAKE_SYNC;

                // Prepare save receiver to accept synchronized save from host
                this.saveReceiver = SaveStreamer.createClientReceiver(new SaveStreamer.SaveTransferCallback() {
                    @Override
                    public void onSaveReceived(Path savedFile) {
                        System.out.println("[CoopSessionManager] Save sync complete. Transitioning to ACTIVE_SIM.");
                        sessionState = SessionState.ACTIVE_SIM;
                    }

                    @Override
                    public void onError(String message, Throwable cause) {
                        System.err.println("[CoopSessionManager] Save sync error: " + message);
                    }
                });

                System.out.println("[CoopSessionManager] Initialized as CLIENT, connected to " + host + ":" + port);
            } catch (IOException e) {
                System.err.println("[CoopSessionManager] Failed to connect to host at " + host + ":" + port + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else if (SteamAPI.isSteamRunning()) {
            this.transport = new SteamP2PTransport();
            System.out.println("[CoopSessionManager] Initialized Steam P2P Transport.");
        }
    }

    /**
     * Frame update hook called once per game render/sim update.
     */
    public void update(double ds) {
        if (transport == null) {
            return;
        }

        transport.update();

        // Drain incoming network packets
        byte[] packet;
        while ((packet = transport.pollIncoming()) != null) {
            if (packet.length == 0) continue;

            byte type = packet[0];
            if (type == SaveStreamer.TYPE_SAVE_START || type == SaveStreamer.TYPE_SAVE_CHUNK || type == SaveStreamer.TYPE_SAVE_END) {
                if (saveReceiver != null) {
                    saveReceiver.handlePacket(packet);
                }
            } else if (type == coop.world.AvatarStatePacket.TYPE_AVATAR_STATE) {
                coop.world.FactionSyncService.getInstance().onPacketReceived(packet);
            } else if (type == coop.battle.BattlePacket.TYPE_BATTLE_INIT ||
                       type == coop.battle.BattlePacket.TYPE_BATTLE_ORDER ||
                       type == coop.battle.BattlePacket.TYPE_BATTLE_EXIT) {
                coop.battle.CoopBattleBridge.onPacketReceived(packet);
            } else {
                commandQueue.offer(packet);
            }
        }

        // Mode B empire state broadcast update
        coop.world.FactionSyncService.getInstance().update(ds);
    }

    /**
     * Host triggers a save capture and streams it to all connected peers.
     */
    public void triggerHostSaveSync() {
        if (role == Role.HOST && transport != null && transport.isConnected()) {
            SaveStreamer.hostSaveAndStream(transport);
        }
    }

    public boolean isSharedCityLockstep() {
        return sessionMode == SessionMode.MODE_A_SHARED_CITY && sessionState == SessionState.ACTIVE_SIM;
    }

    public boolean isSharedWorld() {
        return sessionMode == SessionMode.MODE_B_SHARED_WORLD && sessionState == SessionState.ACTIVE_SIM;
    }

    public boolean isHost() {
        return role == Role.HOST;
    }

    public boolean isClient() {
        return role == Role.CLIENT;
    }

    public boolean isConnected() {
        return transport != null && transport.isConnected();
    }

    public Role getRole() {
        return role;
    }

    public SessionMode getSessionMode() {
        return sessionMode;
    }

    public void setSessionMode(SessionMode sessionMode) {
        this.sessionMode = sessionMode;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    public void setSessionState(SessionState sessionState) {
        this.sessionState = sessionState;
    }

    public CoopTransport getTransport() {
        return transport;
    }

    public Queue<byte[]> getCommandQueue() {
        return commandQueue;
    }

    public void setSaveReceiver(SaveStreamer.Receiver receiver) {
        this.saveReceiver = receiver;
    }

    public synchronized void startHost(int port, SessionMode mode) throws IOException {
        close();
        TcpSocketTransport tcp = new TcpSocketTransport();
        tcp.startHost(port);
        this.transport = tcp;
        this.role = Role.HOST;
        this.sessionMode = mode;
        this.sessionState = SessionState.LOBBY;
        System.out.println("[CoopSessionManager] Host started on port " + port + ", Mode: " + mode);
    }

    public synchronized void connectClient(String host, int port, final SaveStreamer.SaveTransferCallback callback) throws IOException {
        close();
        TcpSocketTransport tcp = new TcpSocketTransport();
        tcp.connect(host, port);
        this.transport = tcp;
        this.role = Role.CLIENT;
        this.sessionState = SessionState.HANDSHAKE_SYNC;

        this.saveReceiver = SaveStreamer.createClientReceiver(new SaveStreamer.SaveTransferCallback() {
            @Override
            public void onSaveReceived(Path savedFile) {
                System.out.println("[CoopSessionManager] Save sync complete: " + savedFile);
                sessionState = SessionState.ACTIVE_SIM;
                if (callback != null) {
                    callback.onSaveReceived(savedFile);
                }
            }

            @Override
            public void onError(String message, Throwable cause) {
                System.err.println("[CoopSessionManager] Save sync error: " + message);
                if (callback != null) {
                    callback.onError(message, cause);
                }
            }
        });

        System.out.println("[CoopSessionManager] Client connected to " + host + ":" + port);
    }

    public int getConnectedPeerCount() {
        if (transport == null || !transport.isConnected()) {
            return 0;
        }
        return 1;
    }

    public synchronized void close() {
        if (transport != null) {
            transport.close();
            transport = null;
        }
        if (saveReceiver != null) {
            saveReceiver.cleanupTempFile();
            saveReceiver = null;
        }
        commandQueue.clear();
        role = Role.NONE;
        sessionState = SessionState.IDLE;
    }
}
