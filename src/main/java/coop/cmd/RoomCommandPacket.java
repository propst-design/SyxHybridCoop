package coop.cmd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import coop.net.CoopSessionManager;
import game.GAME;
import settlement.main.SETT;
import settlement.room.main.RoomBlueprint;
import settlement.room.main.TmpArea;
import settlement.room.main.construction.CONSTRUCTION;
import settlement.room.main.construction.ConstructionData;
import settlement.room.main.construction.ConstructionInit;
import settlement.room.main.furnisher.Furnisher;
import settlement.room.main.util.RoomState;
import settlement.tilemap.terrain.TBuilding;
import snake2d.util.datatypes.COORDINATE;

/**
 * Serializes room blueprint construction, upgrades, deconstruction, and repairs
 * across Mode A lockstep co-rulers.
 */
public class RoomCommandPacket extends CommandPacket {

    public static final byte ACTION_CREATE_CLEAN = 1;
    public static final byte ACTION_CREATE_WITH_DATA = 2;
    public static final byte ACTION_BREAK_IT = 3;
    public static final byte ACTION_CONSTRUCT = 4;

    private final byte action;
    private final int blueprintIndex;
    private final int upgrade;
    private final int actionTx;
    private final int actionTy;
    private final List<TileData> tiles;
    private final RoomState state;

    public static class TileData {
        public final int x;
        public final int y;
        public final int dData;

        public TileData(int x, int y, int dData) {
            this.x = x;
            this.y = y;
            this.dData = dData;
        }
    }

    public RoomCommandPacket(int targetTick, int playerId, long sequence, byte action,
                             int blueprintIndex, int upgrade, int actionTx, int actionTy,
                             List<TileData> tiles, RoomState state) {
        super(TYPE_ROOM, targetTick, playerId, sequence);
        this.action = action;
        this.blueprintIndex = blueprintIndex;
        this.upgrade = upgrade;
        this.actionTx = actionTx;
        this.actionTy = actionTy;
        this.tiles = (tiles != null) ? tiles : new ArrayList<TileData>();
        this.state = state;
    }

    public byte getAction() {
        return action;
    }

    public int getBlueprintIndex() {
        return blueprintIndex;
    }

    public int getUpgrade() {
        return upgrade;
    }

    public List<TileData> getTiles() {
        return tiles;
    }

    public RoomState getState() {
        return state;
    }

    @Override
    public void execute() {
        if (SETT.ROOMS() == null || SETT.ROOMS().construction == null) {
            return;
        }

        CONSTRUCTION cons = SETT.ROOMS().construction;

        if (action == ACTION_CONSTRUCT) {
            cons.construct(actionTx, actionTy);
            return;
        }

        RoomBlueprint blueprint = null;
        if (blueprintIndex >= 0 && blueprintIndex < SETT.ROOMS().all().size()) {
            blueprint = SETT.ROOMS().all().get(blueprintIndex);
        }
        if (blueprint == null) return;

        Furnisher furnisher = null;
        if (blueprint instanceof settlement.room.main.RoomBlueprintImp) {
            furnisher = ((settlement.room.main.RoomBlueprintImp) blueprint).constructor();
        }
        if (furnisher == null) return;
        TBuilding structure = (tiles.isEmpty()) ? null : ConstructionInit.findStructure(tiles.get(0).x, tiles.get(0).y);
        ConstructionInit init = new ConstructionInit(upgrade, furnisher, structure, 0, state);

        TmpArea area = SETT.ROOMS().tmpArea(this);
        for (TileData td : tiles) {
            area.set(td.x, td.y);
            if (action == ACTION_CREATE_WITH_DATA) {
                ConstructionData.dData.set(area, td.x, td.y, td.dData);
            }
        }

        if (action == ACTION_CREATE_CLEAN) {
            cons.createClean(area, init);
        } else if (action == ACTION_CREATE_WITH_DATA) {
            cons.createWithConstructionData(area, init);
        } else if (action == ACTION_BREAK_IT) {
            cons.breakIt(area, init, actionTx, actionTy);
        }
    }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeByte(action);
        out.writeInt(blueprintIndex);
        out.writeInt(upgrade);
        out.writeInt(actionTx);
        out.writeInt(actionTy);

        out.writeInt(tiles.size());
        for (TileData td : tiles) {
            out.writeInt(td.x);
            out.writeInt(td.y);
            out.writeInt(td.dData);
        }

        if (state != null) {
            out.writeBoolean(true);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(state);
            oos.flush();
            byte[] stateBytes = baos.toByteArray();
            out.writeInt(stateBytes.length);
            out.write(stateBytes);
        } else {
            out.writeBoolean(false);
        }
    }

    public static RoomCommandPacket readPayload(int targetTick, int playerId, long sequence, DataInputStream dis) throws IOException {
        byte action = dis.readByte();
        int blueprintIndex = dis.readInt();
        int upgrade = dis.readInt();
        int actionTx = dis.readInt();
        int actionTy = dis.readInt();

        int tileCount = dis.readInt();
        List<TileData> tiles = new ArrayList<TileData>(tileCount);
        for (int i = 0; i < tileCount; i++) {
            int x = dis.readInt();
            int y = dis.readInt();
            int dData = dis.readInt();
            tiles.add(new TileData(x, y, dData));
        }

        RoomState state = null;
        boolean hasState = dis.readBoolean();
        if (hasState) {
            int stateLen = dis.readInt();
            byte[] stateBytes = new byte[stateLen];
            dis.readFully(stateBytes);
            try {
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(stateBytes));
                state = (RoomState) ois.readObject();
            } catch (ClassNotFoundException e) {
                throw new IOException("Failed to deserialize RoomState: " + e.getMessage(), e);
            }
        }

        return new RoomCommandPacket(targetTick, playerId, sequence, action, blueprintIndex, upgrade, actionTx, actionTy, tiles, state);
    }

    // --- Interceptor helpers ---

    public static void interceptConstruct(int tx, int ty) {
        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + CoopLockstepCoordinator.TICK_LEAD;
        RoomCommandPacket pkt = new RoomCommandPacket(
                targetTick,
                CoopLockstepCoordinator.getLocalPlayerId(),
                CoopLockstepCoordinator.nextSequence(),
                ACTION_CONSTRUCT,
                -1, 0, tx, ty, null, null
        );
        broadcast(pkt);
    }

    public static void interceptCreateClean(TmpArea area, ConstructionInit init) {
        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + CoopLockstepCoordinator.TICK_LEAD;
        List<TileData> tiles = extractTiles(area, false);
        int blueprintIndex = (init.b != null && init.b.blue() != null) ? init.b.blue().index() : -1;

        RoomCommandPacket pkt = new RoomCommandPacket(
                targetTick,
                CoopLockstepCoordinator.getLocalPlayerId(),
                CoopLockstepCoordinator.nextSequence(),
                ACTION_CREATE_CLEAN,
                blueprintIndex, init.upgrade, -1, -1, tiles, init.state
        );
        broadcast(pkt);
    }

    public static void interceptCreateWithConstructionData(TmpArea area, ConstructionInit init) {
        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + CoopLockstepCoordinator.TICK_LEAD;
        List<TileData> tiles = extractTiles(area, true);
        int blueprintIndex = (init.b != null && init.b.blue() != null) ? init.b.blue().index() : -1;

        RoomCommandPacket pkt = new RoomCommandPacket(
                targetTick,
                CoopLockstepCoordinator.getLocalPlayerId(),
                CoopLockstepCoordinator.nextSequence(),
                ACTION_CREATE_WITH_DATA,
                blueprintIndex, init.upgrade, -1, -1, tiles, init.state
        );
        broadcast(pkt);
    }

    public static void interceptBreakIt(TmpArea area, ConstructionInit init, int tx, int ty) {
        int currentTick = (GAME.updateI() >= 0) ? GAME.updateI() : 0;
        int targetTick = currentTick + CoopLockstepCoordinator.TICK_LEAD;
        List<TileData> tiles = extractTiles(area, false);
        int blueprintIndex = (init.b != null && init.b.blue() != null) ? init.b.blue().index() : -1;

        RoomCommandPacket pkt = new RoomCommandPacket(
                targetTick,
                CoopLockstepCoordinator.getLocalPlayerId(),
                CoopLockstepCoordinator.nextSequence(),
                ACTION_BREAK_IT,
                blueprintIndex, init.upgrade, tx, ty, tiles, init.state
        );
        broadcast(pkt);
    }

    private static List<TileData> extractTiles(TmpArea area, boolean readData) {
        List<TileData> list = new ArrayList<TileData>();
        if (area == null) return list;
        for (COORDINATE c : area.body()) {
            if (area.is(c)) {
                int d = readData ? ConstructionData.dData.get(c) : 0;
                list.add(new TileData(c.x(), c.y(), d));
            }
        }
        return list;
    }

    private static void broadcast(RoomCommandPacket pkt) {
        CoopLockstepCoordinator.scheduleCommand(pkt);
        if (CoopSessionManager.getInstance().isConnected()) {
            try {
                CoopSessionManager.getInstance().getTransport().sendReliable(pkt.toBytes());
            } catch (IOException e) {
                System.err.println("[RoomCommandPacket] Failed to broadcast room command: " + e.getMessage());
            }
        }
    }
}
