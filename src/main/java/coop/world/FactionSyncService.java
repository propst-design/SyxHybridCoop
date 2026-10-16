package coop.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import coop.cmd.CoopLockstepCoordinator;
import coop.net.CoopSessionManager;
import coop.world.AvatarStatePacket.ArmyData;
import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.npc.FactionNPC;
import init.trade.TR;
import init.trade.TRADABLE;
import world.army.AD;
import world.entity.army.WArmy;
import world.map.regions.Region;
import world.region.RD;
import world.region.pop.RDRace;

/**
 * Service managing periodic 2-second avatar state broadcasts and applying
 * delta state updates to remote players' FactionNPC avatar slots in Mode B.
 */
public class FactionSyncService {

    public static final double SYNC_INTERVAL_SECONDS = 2.0;

    private static final FactionSyncService INSTANCE = new FactionSyncService();

    private double syncTimer = 0.0;

    private FactionSyncService() {}

    public static FactionSyncService getInstance() {
        return INSTANCE;
    }

    public void reset() {
        syncTimer = 0.0;
    }

    /**
     * Periodic update called every frame.
     */
    public void update(double ds) {
        if (!CoopSessionManager.getInstance().isSharedWorld()) {
            return;
        }

        if (!CoopSessionManager.getInstance().isConnected()) {
            return;
        }

        syncTimer += ds;
        if (syncTimer >= SYNC_INTERVAL_SECONDS) {
            syncTimer = 0.0;
            broadcastLocalState();
        }
    }

    /**
     * Collects local player settlement and empire data, serializes to AvatarStatePacket,
     * and broadcasts over the active transport.
     */
    public void broadcastLocalState() {
        if (FACTIONS.player() == null) {
            return;
        }

        int playerId = CoopLockstepCoordinator.getLocalPlayerId();
        int factionIndex = CoopWorldRegistry.getFactionForPlayer(playerId);
        if (factionIndex <= 0) {
            // For host or initial state, fallback to player index (0)
            factionIndex = FACTIONS.player().index();
        }

        // 1. Capitol population
        Map<Integer, Integer> popPerRace = new HashMap<Integer, Integer>();
        Region cap = FACTIONS.player().capitolRegion();
        if (cap != null && RD.RACES() != null && RD.RACES().all != null) {
            for (RDRace r : RD.RACES().all) {
                popPerRace.put(r.index(), r.pop.get(cap));
            }
        }

        // 2. Credits / Denari
        double credits = FACTIONS.player().credits().getD();

        // 3. Trade surpluses / Available resources
        Map<Short, Integer> surpluses = new HashMap<Short, Integer>();
        if (TR.ALL() != null) {
            for (TRADABLE t : TR.ALL()) {
                int avail = FACTIONS.player().res().getAvailable(t);
                surpluses.put((short) t.index(), avail);
            }
        }

        // 4. Diplomacy stances with other active factions
        byte[] stances = new byte[FACTIONS.MAX()];
        for (int i = 0; i < FACTIONS.MAX(); i++) {
            Faction other = FACTIONS.getByIndex(i);
            if (other != null && other.isActive()) {
                stances[i] = (byte) DIP.get(FACTIONS.player(), other).index();
            }
        }

        // 5. Overworld Armies
        List<ArmyData> armies = new ArrayList<ArmyData>();
        if (FACTIONS.player().armies() != null && FACTIONS.player().armies().all() != null) {
            for (int i = 0; i < FACTIONS.player().armies().all().size(); i++) {
                WArmy a = FACTIONS.player().armies().all().get(i);
                short aIdx = a.armyIndex();
                int tx = a.ctx();
                int ty = a.cty();
                int targetX = (a.path() != null && a.path().isValid()) ? a.path().destX() : tx;
                int targetY = (a.path() != null && a.path().isValid()) ? a.path().destY() : ty;
                int men = AD.men(null).get(a);
                String name = (a.name != null) ? a.name.toString() : "";
                armies.add(new ArmyData(aIdx, tx, ty, targetX, targetY, men, name));
            }
        }

        AvatarStatePacket packet = new AvatarStatePacket(playerId, factionIndex, popPerRace, credits, surpluses, stances, armies);

        try {
            byte[] bytes = packet.toBytes();
            CoopSessionManager.getInstance().getTransport().sendReliable(bytes);
        } catch (IOException e) {
            System.err.println("[FactionSyncService] Failed to broadcast AvatarStatePacket: " + e.getMessage());
        }
    }

    /**
     * Handles an incoming AvatarStatePacket from a remote peer.
     */
    public void onPacketReceived(byte[] data) {
        try {
            AvatarStatePacket packet = AvatarStatePacket.parse(data);
            applyAvatarState(packet);
        } catch (Exception e) {
            System.err.println("[FactionSyncService] Error parsing/applying AvatarStatePacket: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Applies delta updates from AvatarStatePacket to the target FactionNPC avatar.
     */
    public void applyAvatarState(AvatarStatePacket packet) {
        int factionIndex = CoopWorldRegistry.getFactionForPlayer(packet.getPlayerId());
        if (factionIndex <= 0 || factionIndex >= FACTIONS.MAX()) {
            factionIndex = packet.getFactionIndex();
        }

        if (factionIndex <= 0 || factionIndex >= FACTIONS.MAX()) {
            return;
        }

        Faction f = FACTIONS.getByIndex(factionIndex);
        if (!(f instanceof FactionNPC)) {
            return;
        }

        FactionNPC avatar = (FactionNPC) f;

        // 1. Synchronize capitol population
        Region cap = avatar.capitolRegion();
        if (cap != null && RD.RACES() != null && RD.RACES().all != null) {
            for (Map.Entry<Integer, Integer> entry : packet.getPopPerRace().entrySet()) {
                int rIdx = entry.getKey();
                if (rIdx >= 0 && rIdx < RD.RACES().all.size()) {
                    RDRace race = RD.RACES().all.get(rIdx);
                    if (race != null) {
                        race.pop.set(cap, entry.getValue());
                    }
                }
            }
        }

        // 2. Synchronize credits
        avatar.credits().set(packet.getCredits());

        // 3. Synchronize stockpile trade surpluses
        if (TR.ALL() != null) {
            for (Map.Entry<Short, Integer> entry : packet.getStockpileSurplus().entrySet()) {
                short resIdx = entry.getKey();
                if (resIdx >= 0 && resIdx < TR.ALL().size()) {
                    TRADABLE res = TR.ALL().get(resIdx);
                    if (res != null) {
                        double current = avatar.stockpile.res(res).amount();
                        avatar.stockpile.res(res).inc(entry.getValue() - current);
                    }
                }
            }
        }

        // 4. Synchronize diplomacy stances
        byte[] stances = packet.getStances();
        if (stances != null) {
            for (int i = 0; i < stances.length && i < FACTIONS.MAX(); i++) {
                Faction other = FACTIONS.getByIndex(i);
                if (other != null && other.isActive() && other != avatar) {
                    DipStance targetStance = getDipStanceByIndex(stances[i]);
                    if (targetStance != null && DIP.get(avatar, other) != targetStance) {
                        targetStance.set(avatar, other);
                    }
                }
            }
        }

        // 5. Synchronize overworld armies
        if (avatar.armies() != null && avatar.armies().all() != null) {
            List<ArmyData> remoteArmies = packet.getArmies();
            for (ArmyData armyData : remoteArmies) {
                WArmy matching = null;
                for (int i = 0; i < avatar.armies().all().size(); i++) {
                    WArmy a = avatar.armies().all().get(i);
                    if (a.armyIndex() == armyData.armyIndex) {
                        matching = a;
                        break;
                    }
                }

                if (matching != null && matching.added()) {
                    // Update position if moved significantly
                    if (Math.abs(matching.ctx() - armyData.tileX) > 1 || Math.abs(matching.cty() - armyData.tileY) > 1) {
                        matching.teleport(armyData.tileX, armyData.tileY);
                    }
                    if (armyData.targetX != armyData.tileX || armyData.targetY != armyData.tileY) {
                        matching.setDestination(armyData.targetX, armyData.targetY);
                    }
                }
            }
        }
    }

    public static DipStance getDipStanceByIndex(int index) {
        switch (index) {
            case 0: return DIP.NEUTRAL();
            case 1: return DIP.WAR();
            case 2: return DIP.TRADE();
            case 3: return DIP.PACT();
            case 4: return DIP.ALLY();
            case 5: return DIP.VASSAL();
            case 6: return DIP.OVERLORD();
            default: return DIP.NEUTRAL();
        }
    }
}
