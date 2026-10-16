package coop.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamNetworking;
import com.codedisaster.steamworks.SteamNetworking.P2PSend;
import com.codedisaster.steamworks.SteamNetworking.P2PSessionError;
import com.codedisaster.steamworks.SteamNetworkingCallback;

/**
 * Steam P2P implementation of CoopTransport using steamworks4j.
 * Handles high-frequency SteamAPI.runCallbacks() polling, P2P packet delivery,
 * and lobby creation/discovery.
 */
public class SteamP2PTransport implements CoopTransport, SteamNetworkingCallback, SteamMatchmakingCallback {

    private static final int CHANNEL_DEFAULT = 0;
    private static final int MAX_PACKET_BUFFER = 1024 * 1024; // 1 MB buffer for reading packets

    private final AtomicBoolean isHost = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final Queue<byte[]> incomingQueue = new ConcurrentLinkedQueue<byte[]>();
    private final List<SteamID> peers = Collections.synchronizedList(new ArrayList<SteamID>());

    private SteamNetworking networking;
    private SteamMatchmaking matchmaking;
    private SteamID currentLobby;
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(MAX_PACKET_BUFFER);
    private final int[] packetSizeHolder = new int[1];

    public SteamP2PTransport() {
        if (SteamAPI.isSteamRunning()) {
            try {
                this.networking = new SteamNetworking(this);
                this.matchmaking = new SteamMatchmaking(this);
            } catch (Throwable t) {
                System.err.println("[SteamP2PTransport] Failed to initialize Steam networking: " + t.getMessage());
            }
        } else {
            System.out.println("[SteamP2PTransport] Steam is not running; P2P transport disabled.");
        }
    }

    @Override
    public void startHost(int maxMembers) throws IOException {
        if (matchmaking == null) {
            throw new IOException("Steam matchmaking is not available.");
        }
        isHost.set(true);
        matchmaking.createLobby(SteamMatchmaking.LobbyType.FriendsOnly, Math.max(2, maxMembers));
    }

    @Override
    public void connect(String lobbyIdStr, int portIgnored) throws IOException {
        if (matchmaking == null) {
            throw new IOException("Steam matchmaking is not available.");
        }
        isHost.set(false);
        try {
            long lobbyId = Long.parseLong(lobbyIdStr);
            SteamID lobbySteamID = SteamID.createFromNativeHandle(lobbyId);
            matchmaking.joinLobby(lobbySteamID);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Steam lobby ID: " + lobbyIdStr, e);
        }
    }

    @Override
    public void sendReliable(byte[] data) throws IOException {
        sendPacket(data, P2PSend.Reliable);
    }

    @Override
    public void sendUnreliable(byte[] data) throws IOException {
        sendPacket(data, P2PSend.UnreliableNoDelay);
    }

    private void sendPacket(byte[] data, P2PSend sendType) throws IOException {
        if (networking == null || data == null || data.length == 0) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(data.length);
        buffer.put(data);
        buffer.flip();

        synchronized (peers) {
            for (SteamID peer : peers) {
                try {
                    buffer.position(0);
                    networking.sendP2PPacket(peer, buffer, sendType, CHANNEL_DEFAULT);
                } catch (SteamException e) {
                    throw new IOException("Steam P2P send failed: " + e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public byte[] pollIncoming() {
        return incomingQueue.poll();
    }

    @Override
    public boolean isConnected() {
        return connected.get() && !peers.isEmpty();
    }

    @Override
    public boolean isHost() {
        return isHost.get();
    }

    @Override
    public void update() {
        if (!SteamAPI.isSteamRunning() || networking == null) {
            return;
        }

        SteamAPI.runCallbacks();

        // Read all available P2P packets
        while (networking.isP2PPacketAvailable(CHANNEL_DEFAULT, packetSizeHolder)) {
            int packetSize = packetSizeHolder[0];
            if (packetSize <= 0 || packetSize > MAX_PACKET_BUFFER) {
                break;
            }

            readBuffer.clear();
            SteamID sender = new SteamID();
            try {
                int readBytes = networking.readP2PPacket(sender, readBuffer, CHANNEL_DEFAULT);
                if (readBytes > 0) {
                    byte[] payload = new byte[readBytes];
                    readBuffer.position(0);
                    readBuffer.get(payload);
                    incomingQueue.offer(payload);
                }
            } catch (SteamException e) {
                System.err.println("[SteamP2PTransport] Error reading P2P packet: " + e.getMessage());
                break;
            }
        }
    }

    @Override
    public void close() {
        connected.set(false);
        if (currentLobby != null && matchmaking != null) {
            matchmaking.leaveLobby(currentLobby);
            currentLobby = null;
        }
        synchronized (peers) {
            if (networking != null) {
                for (SteamID peer : peers) {
                    networking.closeP2PSessionWithUser(peer);
                }
            }
            peers.clear();
        }
        if (matchmaking != null) {
            matchmaking.dispose();
            matchmaking = null;
        }
        if (networking != null) {
            networking.dispose();
            networking = null;
        }
        incomingQueue.clear();
    }

    // --- SteamNetworkingCallback ---

    @Override
    public void onP2PSessionRequest(SteamID steamIDRemote) {
        if (networking != null) {
            networking.acceptP2PSessionWithUser(steamIDRemote);
            if (!peers.contains(steamIDRemote)) {
                peers.add(steamIDRemote);
                connected.set(true);
            }
        }
    }

    @Override
    public void onP2PSessionConnectFail(SteamID steamIDRemote, P2PSessionError sessionError) {
        System.err.println("[SteamP2PTransport] P2P session connect failed with user " + steamIDRemote + ": " + sessionError);
        peers.remove(steamIDRemote);
        if (peers.isEmpty()) {
            connected.set(false);
        }
    }

    // --- SteamMatchmakingCallback stubs ---

    @Override
    public void onLobbyCreated(com.codedisaster.steamworks.SteamResult result, SteamID lobbyID) {
        if (result == com.codedisaster.steamworks.SteamResult.OK) {
            this.currentLobby = lobbyID;
            this.connected.set(true);
            System.out.println("[SteamP2PTransport] Lobby created successfully: " + lobbyID);
        } else {
            System.err.println("[SteamP2PTransport] Lobby creation failed: " + result);
        }
    }

    @Override
    public void onLobbyEnter(SteamID lobbyID, int chatPermissions, boolean blocked, SteamMatchmaking.ChatRoomEnterResponse response) {
        if (response == SteamMatchmaking.ChatRoomEnterResponse.Success) {
            this.currentLobby = lobbyID;
            this.connected.set(true);
            SteamID owner = matchmaking.getLobbyOwner(lobbyID);
            if (owner != null && !peers.contains(owner)) {
                peers.add(owner);
            }
            System.out.println("[SteamP2PTransport] Entered lobby: " + lobbyID);
        } else {
            System.err.println("[SteamP2PTransport] Failed to enter lobby: " + response);
        }
    }

    @Override public void onFavoritesListAccountsUpdated(com.codedisaster.steamworks.SteamResult result) {}
    @Override public void onFavoritesListChanged(int ip, int queryPort, int connPort, int appID, int flags, boolean add, int accountID) {}
    @Override public void onLobbyInvite(SteamID steamIDUser, SteamID steamIDLobby, long gameID) {}
    @Override public void onLobbyMatchList(int lobbiesMatching) {}
    @Override public void onLobbyKicked(SteamID lobbyID, SteamID adminID, boolean kickedDueToDisconnect) {}
    @Override public void onLobbyChatUpdate(SteamID lobbyID, SteamID changedID, SteamID makingChangeID, SteamMatchmaking.ChatMemberStateChange stateChange) {}
    @Override public void onLobbyChatMessage(SteamID lobbyID, SteamID chatterID, SteamMatchmaking.ChatEntryType chatEntryType, int chatID) {}
    @Override public void onLobbyGameCreated(SteamID lobbyID, SteamID gameServerID, int ip, short port) {}
    @Override public void onLobbyDataUpdate(SteamID lobbyID, SteamID memberID, boolean success) {}
}
