package coop.world;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import game.faction.FACTIONS;
import game.faction.Faction;
import game.faction.npc.FactionNPC;
import snake2d.util.file.FileGetter;
import snake2d.util.file.FilePutter;
import world.map.regions.Region;
import world.region.pop.RDRace;

/**
 * Registry maintaining player-to-faction mappings for Mode B (Shared-World Co-Op).
 * Remote player empires in the overworld are backed by reserved FactionNPC slots
 * with autonomous AI decision-making suppressed.
 */
public class CoopWorldRegistry {

    private static final Map<Integer, Integer> playerToFaction = new ConcurrentHashMap<Integer, Integer>();
    private static final Map<Integer, Integer> factionToPlayer = new ConcurrentHashMap<Integer, Integer>();
    private static final boolean[] isAvatar = new boolean[FACTIONS.MAX()];

    public static boolean isPlayerAvatar(int factionIndex) {
        if (factionIndex < 0 || factionIndex >= isAvatar.length) {
            return false;
        }
        return isAvatar[factionIndex];
    }

    public static int getFactionForPlayer(int playerId) {
        Integer idx = playerToFaction.get(playerId);
        return idx != null ? idx : -1;
    }

    public static int getPlayerForFaction(int factionIndex) {
        Integer pid = factionToPlayer.get(factionIndex);
        return pid != null ? pid : -1;
    }

    public static synchronized void bindPlayerAvatar(int playerId, int factionIndex) {
        if (factionIndex <= 0 || factionIndex >= FACTIONS.MAX()) {
            throw new IllegalArgumentException("Invalid faction index for player avatar: " + factionIndex);
        }

        playerToFaction.put(playerId, factionIndex);
        factionToPlayer.put(factionIndex, playerId);
        isAvatar[factionIndex] = true;

        try {
            Faction f = FACTIONS.getByIndex(factionIndex);
            if (f instanceof FactionNPC) {
                ((FactionNPC) f).sanctified = true;
            }
        } catch (Throwable ignored) {
            // Uninitialized in headless unit test environments
        }

        System.out.println("[CoopWorldRegistry] Bound Player " + playerId + " to FactionNPC [" + factionIndex + "]");
    }

    public static synchronized void unbindPlayerAvatar(int playerId) {
        Integer factionIndex = playerToFaction.remove(playerId);
        if (factionIndex != null) {
            factionToPlayer.remove(factionIndex);
            if (factionIndex >= 0 && factionIndex < isAvatar.length) {
                isAvatar[factionIndex] = false;
            }
            System.out.println("[CoopWorldRegistry] Unbound Player " + playerId + " from FactionNPC [" + factionIndex + "]");
        }
    }

    public static synchronized FactionNPC allocatePlayerAvatar(int playerId, Region capitol, RDRace prefRace) {
        FactionNPC npc = FACTIONS.activateNext(capitol, prefRace, false);
        if (npc != null) {
            bindPlayerAvatar(playerId, npc.index());
        }
        return npc;
    }

    public static synchronized void reset() {
        playerToFaction.clear();
        factionToPlayer.clear();
        Arrays.fill(isAvatar, false);
    }

    public static synchronized void save(FilePutter file) {
        file.i(playerToFaction.size());
        for (Map.Entry<Integer, Integer> entry : playerToFaction.entrySet()) {
            file.i(entry.getKey());
            file.i(entry.getValue());
        }
    }

    public static synchronized void load(FileGetter file) throws IOException {
        reset();
        int count = file.i();
        for (int i = 0; i < count; i++) {
            int pid = file.i();
            int fidx = file.i();
            bindPlayerAvatar(pid, fidx);
        }
    }
}
