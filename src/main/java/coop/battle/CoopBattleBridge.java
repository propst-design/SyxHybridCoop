package coop.battle;

import java.io.IOException;
import java.nio.file.Path;

import coop.net.CoopSessionManager;
import coop.net.SaveStreamer;
import coop.world.CoopWorldRegistry;
import game.GAME;
import game.battle.div.Div;
import game.battle.state.BattleState;
import game.battle.state.BattleStateExiter;
import game.battle.state.BattleStateResult;
import game.faction.FACTIONS;
import game.faction.Faction;
import init.paths.PATHS;
import snake2d.util.datatypes.DIR;
import world.battle.Side;
import world.battle.spec.BATTLE_RESULT;

/**
 * Bridges Mode B (Shared-World Co-Op) into Mode A (Shared-City/Battle lockstep)
 * when two human-controlled player factions clash on the overworld.
 */
public class CoopBattleBridge {

    private static volatile boolean opponentHuman = false;
    private static volatile boolean localPlayerOpponent = false;
    private static volatile boolean inLiveBattle = false;

    public static boolean isOpponentHuman() {
        return opponentHuman;
    }

    public static void setOpponentHuman(boolean val) {
        opponentHuman = val;
    }

    public static boolean isLocalPlayerOpponent() {
        return localPlayerOpponent;
    }

    public static void setLocalPlayerOpponent(boolean val) {
        localPlayerOpponent = val;
    }

    public static boolean isInLiveBattle() {
        return inLiveBattle;
    }

    /**
     * Checks if a world battle clash involves a human player and a remote player avatar.
     * If so, initiates the live tactical battle bridge.
     */
    public static boolean checkAndInitiateBattle(Side A, Side B) {
        if (!CoopSessionManager.getInstance().isSharedWorld()) {
            return false;
        }
        if (!CoopSessionManager.getInstance().isConnected()) {
            return false;
        }
        if (A == null || B == null || A.us.size() == 0 || B.us.size() == 0) {
            return false;
        }

        Faction factionA = A.us.get(0).faction();
        Faction factionB = B.us.get(0).faction();
        if (factionA == null || factionB == null) {
            return false;
        }

        boolean aIsPlayer = (factionA == FACTIONS.player());
        boolean bIsPlayer = (factionB == FACTIONS.player());

        boolean aIsAvatar = CoopWorldRegistry.isPlayerAvatar(factionA.index());
        boolean bIsAvatar = CoopWorldRegistry.isPlayerAvatar(factionB.index());

        if ((aIsPlayer && bIsAvatar) || (bIsPlayer && aIsAvatar)) {
            System.out.println("[CoopBattleBridge] Human-vs-Human clash detected between " + factionA.name + " and " + factionB.name);
            startLiveTacticalBattle(aIsPlayer ? A : B, aIsPlayer ? B : A);
            return true;
        }

        return false;
    }

    /**
     * Checks if a world siege involves a human player and a remote player avatar.
     */
    public static boolean checkAndInitiateBesiege(Side besieger, Side besieged) {
        return checkAndInitiateBattle(besieger, besieged);
    }

    /**
     * Initiates the tactical battle transition.
     */
    public static void startLiveTacticalBattle(Side playerSide, Side enemySide) {
        opponentHuman = true;
        inLiveBattle = true;

        if (CoopSessionManager.getInstance().isHost()) {
            localPlayerOpponent = false;
            CoopSessionManager.getInstance().setSessionState(CoopSessionManager.SessionState.BATTLE_SUBSTATE);

            int atkFaction = (playerSide.us.size() > 0) ? playerSide.us.get(0).faction().index() : 0;
            int defFaction = (enemySide.us.size() > 0) ? enemySide.us.get(0).faction().index() : 0;

            // Broadcast BATTLE_INIT packet to Client
            BattlePacket initPacket = BattlePacket.createInit(atkFaction, defFaction, 0, 0);
            try {
                CoopSessionManager.getInstance().getTransport().sendReliable(initPacket.toBytes());
            } catch (IOException e) {
                System.err.println("[CoopBattleBridge] Failed to send BattlePacket init: " + e.getMessage());
            }

            // Stream generated __battle save file once created
            triggerHostBattleGeneration(playerSide, enemySide);
        } else {
            localPlayerOpponent = true;
            CoopSessionManager.getInstance().setSessionState(CoopSessionManager.SessionState.BATTLE_SUBSTATE);
        }
    }

    private static void triggerHostBattleGeneration(Side playerSide, Side enemySide) {
        try {
            // Stream __battle save to client if it exists
            Path battleSavePath = PATHS.local().save().get("__battle");
            if (battleSavePath != null && battleSavePath.toFile().exists()) {
                SaveStreamer.streamFile(battleSavePath, CoopSessionManager.getInstance().getTransport());
            }
        } catch (Exception e) {
            System.err.println("[CoopBattleBridge] Error preparing battle stream on Host: " + e.getMessage());
        }
    }

    /**
     * Handles incoming battle control packets from remote peer.
     */
    public static void onPacketReceived(byte[] data) {
        try {
            BattlePacket packet = BattlePacket.parse(data);
            if (packet.type == BattlePacket.TYPE_BATTLE_INIT) {
                System.out.println("[CoopBattleBridge] Received BATTLE_INIT from Host. Preparing tactical battle state...");
                opponentHuman = true;
                localPlayerOpponent = true;
                inLiveBattle = true;
                CoopSessionManager.getInstance().setSessionState(CoopSessionManager.SessionState.BATTLE_SUBSTATE);

                // Setup save receiver to load incoming __battle.save
                CoopSessionManager.getInstance().setSaveReceiver(SaveStreamer.createClientReceiver(new SaveStreamer.SaveTransferCallback() {
                    @Override
                    public void onSaveReceived(Path savedFile) {
                        System.out.println("[CoopBattleBridge] Received __battle.save from Host. Entering battle view...");
                        BattleStateExiter exiter = new BattleStateExiter() {
                            @Override
                            public void afterExit(BattleStateResult res) {
                                onBattleConcluded(res.result, res.playerLosses, res.enemyLosses);
                            }
                        };
                        BattleState.setLoaded(exiter, savedFile, true);
                    }

                    @Override
                    public void onError(String message, Throwable cause) {
                        System.err.println("[CoopBattleBridge] Error receiving battle save: " + message);
                    }
                }));

            } else if (packet.type == BattlePacket.TYPE_BATTLE_ORDER) {
                applyRemoteDivisionOrder(packet);
            } else if (packet.type == BattlePacket.TYPE_BATTLE_EXIT) {
                BATTLE_RESULT result = mapResultCode(packet.resultCode);
                onBattleConcluded(result, packet.playerLosses, packet.enemyLosses);
            }
        } catch (Exception e) {
            System.err.println("[CoopBattleBridge] Error processing battle packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Applies remote division orders received over the network.
     */
    public static void applyRemoteDivisionOrder(BattlePacket packet) {
        try {
            if (GAME.ARMIES() == null) return;
            Div div = GAME.ARMIES().division(packet.divIndex);
            if (div != null) {
                div.settings().musteringSet(packet.mustering);
                if (packet.destX >= 0 && packet.destY >= 0 && GAME.ARMIES().placer != null) {
                    GAME.ARMIES().placer.deploy(div, packet.destX, packet.destY);
                }
            }
        } catch (Throwable t) {
            System.err.println("[CoopBattleBridge] Error applying division order: " + t.getMessage());
        }
    }

    /**
     * Broadcasts a division order to the remote peer during tactical combat.
     */
    public static void broadcastDivisionOrder(short divIndex, int destX, int destY, int dir, boolean mustering) {
        if (!inLiveBattle || !opponentHuman || !CoopSessionManager.getInstance().isConnected()) {
            return;
        }

        try {
            BattlePacket p = BattlePacket.createOrder(divIndex, destX, destY, dir, mustering);
            CoopSessionManager.getInstance().getTransport().sendReliable(p.toBytes());
        } catch (IOException e) {
            System.err.println("[CoopBattleBridge] Failed to broadcast division order: " + e.getMessage());
        }
    }

    /**
     * Restores overworld simulation state and notifies peer when battle ends.
     */
    public static void onBattleConcluded(BATTLE_RESULT result, int playerLosses, int enemyLosses) {
        System.out.println("[CoopBattleBridge] Battle concluded: " + result + ". Transitioning back to Mode B.");
        opponentHuman = false;
        localPlayerOpponent = false;
        inLiveBattle = false;
        CoopSessionManager.getInstance().setSessionState(CoopSessionManager.SessionState.ACTIVE_SIM);

        if (CoopSessionManager.getInstance().isConnected()) {
            try {
                BattlePacket exitPacket = BattlePacket.createExit(result.ordinal(), playerLosses, enemyLosses);
                CoopSessionManager.getInstance().getTransport().sendReliable(exitPacket.toBytes());
            } catch (IOException ignored) {}
        }
    }

    private static BATTLE_RESULT mapResultCode(int code) {
        BATTLE_RESULT[] values = BATTLE_RESULT.values();
        if (code >= 0 && code < values.length) {
            return values[code];
        }
        return BATTLE_RESULT.VICTORY;
    }
}
