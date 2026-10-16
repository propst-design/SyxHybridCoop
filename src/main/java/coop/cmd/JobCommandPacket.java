package coop.cmd;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import coop.net.CoopSessionManager;
import game.GAME;
import settlement.job.Job;
import settlement.main.SETT;
import snake2d.util.datatypes.AREA;
import snake2d.util.datatypes.RECTANGLE;
import snake2d.util.datatypes.Rec;
import view.tool.PLACER_TYPE;
import view.tool.PlacableMulti;

/**
 * Serializes tile job placements, road building, structure construction,
 * clearings, activations, and cancellations across Mode A lockstep co-rulers.
 */
public class JobCommandPacket extends CommandPacket {

    public static final byte TOOL_JOB        = 1;
    public static final byte TOOL_CLEAR      = 2;
    public static final byte TOOL_ACTIVATE   = 3;
    public static final byte TOOL_DORMANT    = 4;
    public static final byte TOOL_REMOVE_ALL = 5;
    public static final byte TOOL_REPAIR     = 6;

    private final byte toolType;
    private final int jobIndex;
    private final int placerTypeIndex;
    private final List<int[]> coordinates;

    public JobCommandPacket(int targetTick, int playerId, long sequence, byte toolType,
                            int jobIndex, int placerTypeIndex, List<int[]> coordinates) {
        super(TYPE_JOB, targetTick, playerId, sequence);
        this.toolType = toolType;
        this.jobIndex = jobIndex;
        this.placerTypeIndex = placerTypeIndex;
        this.coordinates = (coordinates != null) ? coordinates : new ArrayList<int[]>();
    }

    public byte getToolType() {
        return toolType;
    }

    public int getJobIndex() {
        return jobIndex;
    }

    public int getPlacerTypeIndex() {
        return placerTypeIndex;
    }

    public List<int[]> getCoordinates() {
        return coordinates;
    }

    @Override
    public void execute() {
        if (SETT.JOBS() == null || coordinates.isEmpty()) {
            return;
        }

        PlacableMulti placable = resolvePlacable(toolType, jobIndex);
        if (placable == null) {
            return;
        }

        PLACER_TYPE type = (placerTypeIndex >= 0 && placerTypeIndex < PLACER_TYPE.all.size())
                ? PLACER_TYPE.all.get(placerTypeIndex)
                : PLACER_TYPE.SQUARE;

        SimpleArea area = new SimpleArea(coordinates);

        for (int[] coord : coordinates) {
            try {
                placable.place(coord[0], coord[1], area, type);
            } catch (Throwable t) {
                System.err.println("[JobCommandPacket] Error placing tile (" + coord[0] + ", " + coord[1] + "): " + t.getMessage());
            }
        }
        placable.finishPlacing(area);
    }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeByte(toolType);
        out.writeInt(jobIndex);
        out.writeInt(placerTypeIndex);

        out.writeInt(coordinates.size());
        for (int[] coord : coordinates) {
            out.writeInt(coord[0]);
            out.writeInt(coord[1]);
        }
    }

    public static JobCommandPacket readPayload(int targetTick, int playerId, long sequence, DataInputStream dis) throws IOException {
        byte toolType = dis.readByte();
        int jobIndex = dis.readInt();
        int placerTypeIndex = dis.readInt();

        int count = dis.readInt();
        List<int[]> coords = new ArrayList<int[]>(count);
        for (int i = 0; i < count; i++) {
            int x = dis.readInt();
            int y = dis.readInt();
            coords.add(new int[]{x, y});
        }

        return new JobCommandPacket(targetTick, playerId, sequence, toolType, jobIndex, placerTypeIndex, coords);
    }

    public static PlacableMulti resolvePlacable(byte toolType, int jobIndex) {
        if (SETT.JOBS() == null) return null;

        switch (toolType) {
            case TOOL_CLEAR:
                return SETT.JOBS().tool_clear;
            case TOOL_ACTIVATE:
                return SETT.JOBS().tool_activate;
            case TOOL_DORMANT:
                return SETT.JOBS().tool_dormant;
            case TOOL_REMOVE_ALL:
                return SETT.JOBS().tool_remove_all;
            case TOOL_REPAIR:
                return SETT.JOBS().tool_repair;
            case TOOL_JOB:
                if (SETT.JOBS() != null) {
                    Job j = settlement.job.JOBS.getJob(jobIndex);
                    return (j != null) ? j.placer() : null;
                }
                return null;
            default:
                return null;
        }
    }

    public static byte getToolTypeFor(PlacableMulti placable) {
        if (SETT.JOBS() == null || placable == null) return 0;
        if (placable == SETT.JOBS().tool_clear) return TOOL_CLEAR;
        if (placable == SETT.JOBS().tool_activate) return TOOL_ACTIVATE;
        if (placable == SETT.JOBS().tool_dormant) return TOOL_DORMANT;
        if (placable == SETT.JOBS().tool_remove_all) return TOOL_REMOVE_ALL;
        if (placable == SETT.JOBS().tool_repair) return TOOL_REPAIR;

        int count = settlement.job.JOBS.getJobCount();
        for (int i = 0; i < count; i++) {
            Job j = settlement.job.JOBS.getJob(i);
            if (j != null && j.placer() == placable) {
                return TOOL_JOB;
            }
        }
        return 0;
    }

    public static int getJobIndexFor(PlacableMulti placable) {
        int count = settlement.job.JOBS.getJobCount();
        for (int i = 0; i < count; i++) {
            Job j = settlement.job.JOBS.getJob(i);
            if (j != null && j.placer() == placable) {
                return i;
            }
        }
        return -1;
    }

    public static void broadcastJobPlacement(PlacableMulti placable, PLACER_TYPE type, List<int[]> coordinates) {
        byte toolType = getToolTypeFor(placable);
        int jobIndex = getJobIndexFor(placable);
        int placerTypeIndex = 0;
        for (int i = 0; i < PLACER_TYPE.all.size(); i++) {
            if (PLACER_TYPE.all.get(i) == type) {
                placerTypeIndex = i;
                break;
            }
        }

        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + CoopLockstepCoordinator.TICK_LEAD;

        JobCommandPacket pkt = new JobCommandPacket(
                targetTick,
                CoopLockstepCoordinator.getLocalPlayerId(),
                CoopLockstepCoordinator.nextSequence(),
                toolType, jobIndex, placerTypeIndex, coordinates
        );

        CoopLockstepCoordinator.scheduleCommand(pkt);
        if (CoopSessionManager.getInstance().isConnected()) {
            try {
                CoopSessionManager.getInstance().getTransport().sendReliable(pkt.toBytes());
            } catch (IOException e) {
                System.err.println("[JobCommandPacket] Failed to broadcast job command: " + e.getMessage());
            }
        }
    }

    public static class SimpleArea implements AREA {
        private final Rec bounds = new Rec();
        private final List<int[]> coords;

        public SimpleArea(List<int[]> coords) {
            this.coords = coords;
            if (!coords.isEmpty()) {
                bounds.set(coords.get(0)[0], coords.get(0)[1], coords.get(0)[0], coords.get(0)[1]);
                for (int[] c : coords) {
                    bounds.unify(c[0], c[1]);
                }
            }
        }

        @Override
        public int area() {
            return coords.size();
        }

        @Override
        public RECTANGLE body() {
            return bounds;
        }

        @Override
        public boolean is(int tx, int ty) {
            for (int[] c : coords) {
                if (c[0] == tx && c[1] == ty) return true;
            }
            return false;
        }

        @Override
        public boolean is(int tile) {
            int tx = tile % SETT.TWIDTH;
            int ty = tile / SETT.TWIDTH;
            return is(tx, ty);
        }
    }
}
