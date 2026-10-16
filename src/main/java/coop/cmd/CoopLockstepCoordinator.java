package coop.cmd;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import coop.net.CoopSessionManager;
import game.GAME;
import settlement.main.SETT;
import settlement.stats.STATS;
import snake2d.util.rnd.RND;

/**
 * Coordinates deterministic lockstep tick progression, tick barriers,
 * command scheduling, and rolling checksum desync detection for Mode A.
 */
public class CoopLockstepCoordinator {

    public static final int TICK_LEAD = 3; // ~48 ms lead time for command application
    public static final int CHECKSUM_INTERVAL = 16; // Once per simulation second (16 ticks)

    private static volatile boolean applyingRemoteCommand = false;
    private static final AtomicLong sequenceCounter = new AtomicLong(0);

    // Scheduled commands mapped by target tick
    private static final Map<Integer, List<CommandPacket>> scheduledCommands = new ConcurrentHashMap<Integer, List<CommandPacket>>();

    // Remote peer confirmations: remotePlayerId -> highest confirmed tick
    private static final Map<Integer, Integer> peerConfirmedTicks = new ConcurrentHashMap<Integer, Integer>();

    // Rolling checksum history: tick -> (playerId -> checksum)
    private static final Map<Integer, Map<Integer, Long>> checksumHistory = new ConcurrentHashMap<Integer, Map<Integer, Long>>();

    private static final Comparator<CommandPacket> DETERMINISTIC_ORDER = new Comparator<CommandPacket>() {
        @Override
        public int compare(CommandPacket o1, CommandPacket o2) {
            if (o1.playerId != o2.playerId) {
                return Integer.compare(o1.playerId, o2.playerId);
            }
            return Long.compare(o1.sequence, o2.sequence);
        }
    };

    public static boolean isApplyingRemoteCommand() {
        return applyingRemoteCommand;
    }

    public static void setApplyingRemoteCommand(boolean applying) {
        applyingRemoteCommand = applying;
    }

    public static int getCurrentTick() {
        return (game.GAME.updateI() >= 0) ? game.GAME.updateI() : 0;
    }

    public static int getLocalPlayerId() {
        return CoopSessionManager.getInstance().isHost() ? 0 : 1;
    }

    public static long nextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    /**
     * Broadcasts a speed change command scheduled TICK_LEAD ticks into the future.
     */
    public static void broadcastSpeedCommand(double targetSpeed) {
        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + TICK_LEAD;
        int playerId = getLocalPlayerId();
        long seq = nextSequence();

        SpeedCommand cmd = new SpeedCommand(targetTick, playerId, seq, targetSpeed);
        scheduleCommand(cmd);

        if (CoopSessionManager.getInstance().isConnected()) {
            try {
                CoopSessionManager.getInstance().getTransport().sendReliable(cmd.toBytes());
            } catch (IOException e) {
                System.err.println("[CoopLockstepCoordinator] Failed to broadcast SpeedCommand: " + e.getMessage());
            }
        }
    }

    public static void scheduleCommand(CommandPacket cmd) {
        List<CommandPacket> list = scheduledCommands.get(cmd.getTargetTick());
        if (list == null) {
            list = Collections.synchronizedList(new ArrayList<CommandPacket>());
            List<CommandPacket> existing = scheduledCommands.putIfAbsent(cmd.getTargetTick(), list);
            if (existing != null) {
                list = existing;
            }
        }
        list.add(cmd);
    }

    /**
     * Barrier check: returns true if the simulation can advance to step 'tick'.
     * In lockstep, peers must acknowledge ticks to prevent timestep drift.
     */
    public static boolean canAdvanceTick(int tick) {
        boolean isLockstep = CoopSessionManager.getInstance().isSharedCityLockstep() ||
                             CoopSessionManager.getInstance().getSessionState() == CoopSessionManager.SessionState.BATTLE_SUBSTATE;
        if (!isLockstep) {
            return true;
        }
        if (!CoopSessionManager.getInstance().isConnected()) {
            return true;
        }

        // Drain any pending network packets first
        drainIncomingPackets();

        // Check if remote peer has reached at least tick - TICK_LEAD
        int remotePlayerId = CoopSessionManager.getInstance().isHost() ? 1 : 0;
        Integer peerTick = peerConfirmedTicks.get(remotePlayerId);
        if (peerTick == null) {
            // Give a small initial grace window at the very beginning of the session
            return tick < TICK_LEAD;
        }

        return peerTick >= (tick - TICK_LEAD);
    }

    /**
     * Drains incoming network packets from the queue and routes them to schedules/barriers.
     */
    public static void drainIncomingPackets() {
        Queue<byte[]> queue = CoopSessionManager.getInstance().getCommandQueue();
        byte[] data;
        while ((data = queue.poll()) != null) {
            try {
                CommandPacket packet = CommandPacket.parse(data);
                if (packet == null) continue;

                if (packet instanceof TickFramePacket) {
                    TickFramePacket tf = (TickFramePacket) packet;
                    peerConfirmedTicks.put(tf.getPlayerId(), tf.getTargetTick());

                    if (tf.getStateChecksum() != 0) {
                        recordChecksum(tf.getTargetTick(), tf.getPlayerId(), tf.getStateChecksum());
                    }
                } else {
                    scheduleCommand(packet);
                }
            } catch (IOException e) {
                System.err.println("[CoopLockstepCoordinator] Error parsing incoming command packet: " + e.getMessage());
            }
        }
    }

    /**
     * Called at the start of tick 'tick' to apply all commands deterministically.
     */
    public static void preTick(int tick) {
        drainIncomingPackets();

        List<CommandPacket> commands = scheduledCommands.remove(tick);
        if (commands != null && !commands.isEmpty()) {
            Collections.sort(commands, DETERMINISTIC_ORDER);
            applyingRemoteCommand = true;
            try {
                for (CommandPacket cmd : commands) {
                    try {
                        cmd.execute();
                    } catch (Throwable t) {
                        System.err.println("[CoopLockstepCoordinator] Error executing command at tick " + tick + ": " + t.getMessage());
                        t.printStackTrace();
                    }
                }
            } finally {
                applyingRemoteCommand = false;
            }
        }
    }

    /**
     * Called after tick 'tick' completes to send tick confirmation and rolling checksum.
     */
    public static void postTick(int tick) {
        long checksum = 0;
        if (tick % CHECKSUM_INTERVAL == 0) {
            checksum = computeStateChecksum(tick);
            recordChecksum(tick, getLocalPlayerId(), checksum);
            verifyChecksums(tick);
        }

        // Broadcast tick frame to peers
        if (CoopSessionManager.getInstance().isSharedCityLockstep() && CoopSessionManager.getInstance().isConnected()) {
            TickFramePacket tf = new TickFramePacket(tick, getLocalPlayerId(), nextSequence(), checksum);
            try {
                CoopSessionManager.getInstance().getTransport().sendReliable(tf.toBytes());
            } catch (IOException e) {
                System.err.println("[CoopLockstepCoordinator] Failed to broadcast TickFramePacket: " + e.getMessage());
            }
        }

        // Clean up old history
        if (tick > 128) {
            checksumHistory.remove(tick - 128);
            scheduledCommands.remove(tick - 128);
        }
    }

    private static void recordChecksum(int tick, int playerId, long checksum) {
        Map<Integer, Long> map = checksumHistory.get(tick);
        if (map == null) {
            map = new ConcurrentHashMap<Integer, Long>();
            Map<Integer, Long> existing = checksumHistory.putIfAbsent(tick, map);
            if (existing != null) {
                map = existing;
            }
        }
        map.put(playerId, checksum);
    }

    private static void verifyChecksums(int tick) {
        Map<Integer, Long> map = checksumHistory.get(tick);
        if (map != null && map.size() >= 2) {
            Long hostHash = map.get(0);
            Long clientHash = map.get(1);
            if (hostHash != null && clientHash != null && !hostHash.equals(clientHash)) {
                System.err.println("[CoopLockstepCoordinator] DESYNC DETECTED at tick " + tick + "! Host: " + hostHash + ", Client: " + clientHash);
                // Host triggers automatic resync
                if (CoopSessionManager.getInstance().isHost()) {
                    System.out.println("[CoopLockstepCoordinator] Host initiating desync recovery snapshot...");
                    CoopSessionManager.getInstance().triggerHostSaveSync();
                }
            }
        }
    }

    /**
     * Computes a deterministic state checksum across RND seed, entities, and population.
     */
    public static long computeStateChecksum(int tick) {
        long hash = 17;
        hash = 31 * hash + tick;
        hash = 31 * hash + RND.seed();

        try {
            if (SETT.ENTITIES() != null) {
                hash = 31 * hash + SETT.ENTITIES().Imax();
            }
            if (STATS.POP() != null && STATS.POP().POP != null && STATS.POP().POP.data() != null) {
                hash = 31 * hash + STATS.POP().POP.data().get(null);
            }
        } catch (Throwable ignored) {}

        return hash;
    }

    public static void reset() {
        scheduledCommands.clear();
        peerConfirmedTicks.clear();
        checksumHistory.clear();
        applyingRemoteCommand = false;
        sequenceCounter.set(0);
    }
}
