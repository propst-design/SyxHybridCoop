package coop.world;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * State broadcast packet for Mode B (Shared-World Co-Op).
 * Transmitted every 2 seconds to synchronize capitol population, treasury denari,
 * trade surplus stockpiles, diplomacy stances, and overworld armies.
 */
public class AvatarStatePacket {

    public static final byte TYPE_AVATAR_STATE = 0x20;

    private final int playerId;
    private final int factionIndex;
    private final Map<Integer, Integer> popPerRace;
    private final double credits;
    private final Map<Short, Integer> stockpileSurplus;
    private final byte[] stances;
    private final List<ArmyData> armies;

    public static class ArmyData {
        public final short armyIndex;
        public final int tileX;
        public final int tileY;
        public final int targetX;
        public final int targetY;
        public final int totalMen;
        public final String name;

        public ArmyData(short armyIndex, int tileX, int tileY, int targetX, int targetY, int totalMen, String name) {
            this.armyIndex = armyIndex;
            this.tileX = tileX;
            this.tileY = tileY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.totalMen = totalMen;
            this.name = name != null ? name : "";
        }
    }

    public AvatarStatePacket(int playerId, int factionIndex, Map<Integer, Integer> popPerRace,
                             double credits, Map<Short, Integer> stockpileSurplus,
                             byte[] stances, List<ArmyData> armies) {
        this.playerId = playerId;
        this.factionIndex = factionIndex;
        this.popPerRace = popPerRace != null ? popPerRace : new HashMap<Integer, Integer>();
        this.credits = credits;
        this.stockpileSurplus = stockpileSurplus != null ? stockpileSurplus : new HashMap<Short, Integer>();
        this.stances = stances != null ? stances : new byte[0];
        this.armies = armies != null ? armies : new ArrayList<ArmyData>();
    }

    public int getPlayerId() {
        return playerId;
    }

    public int getFactionIndex() {
        return factionIndex;
    }

    public Map<Integer, Integer> getPopPerRace() {
        return popPerRace;
    }

    public double getCredits() {
        return credits;
    }

    public Map<Short, Integer> getStockpileSurplus() {
        return stockpileSurplus;
    }

    public byte[] getStances() {
        return stances;
    }

    public List<ArmyData> getArmies() {
        return armies;
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(TYPE_AVATAR_STATE);
        dos.writeInt(playerId);
        dos.writeInt(factionIndex);

        // Populations per race
        dos.writeShort(popPerRace.size());
        for (Map.Entry<Integer, Integer> entry : popPerRace.entrySet()) {
            dos.writeInt(entry.getKey());
            dos.writeInt(entry.getValue());
        }

        // Credits / Treasury
        dos.writeDouble(credits);

        // Stockpile surplus
        dos.writeShort(stockpileSurplus.size());
        for (Map.Entry<Short, Integer> entry : stockpileSurplus.entrySet()) {
            dos.writeShort(entry.getKey());
            dos.writeInt(entry.getValue());
        }

        // Diplomacy stances
        dos.writeShort(stances.length);
        dos.write(stances);

        // Armies
        dos.writeShort(armies.size());
        for (ArmyData army : armies) {
            dos.writeShort(army.armyIndex);
            dos.writeInt(army.tileX);
            dos.writeInt(army.tileY);
            dos.writeInt(army.targetX);
            dos.writeInt(army.targetY);
            dos.writeInt(army.totalMen);
            dos.writeUTF(army.name);
        }

        dos.flush();
        return baos.toByteArray();
    }

    public static AvatarStatePacket parse(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        byte type = dis.readByte();
        if (type != TYPE_AVATAR_STATE) {
            throw new IOException("Invalid packet type for AvatarStatePacket: 0x" + Integer.toHexString(type & 0xFF));
        }

        int playerId = dis.readInt();
        int factionIndex = dis.readInt();

        // Populations per race
        int raceCount = dis.readShort() & 0xFFFF;
        Map<Integer, Integer> popPerRace = new HashMap<Integer, Integer>(raceCount);
        for (int i = 0; i < raceCount; i++) {
            int raceIdx = dis.readInt();
            int pop = dis.readInt();
            popPerRace.put(raceIdx, pop);
        }

        // Credits
        double credits = dis.readDouble();

        // Stockpile surplus
        int resCount = dis.readShort() & 0xFFFF;
        Map<Short, Integer> stockpileSurplus = new HashMap<Short, Integer>(resCount);
        for (int i = 0; i < resCount; i++) {
            short resIdx = dis.readShort();
            int amount = dis.readInt();
            stockpileSurplus.put(resIdx, amount);
        }

        // Diplomacy stances
        int stanceCount = dis.readShort() & 0xFFFF;
        byte[] stances = new byte[stanceCount];
        dis.readFully(stances);

        // Armies
        int armyCount = dis.readShort() & 0xFFFF;
        List<ArmyData> armies = new ArrayList<ArmyData>(armyCount);
        for (int i = 0; i < armyCount; i++) {
            short armyIndex = dis.readShort();
            int tileX = dis.readInt();
            int tileY = dis.readInt();
            int targetX = dis.readInt();
            int targetY = dis.readInt();
            int totalMen = dis.readInt();
            String name = dis.readUTF();
            armies.add(new ArmyData(armyIndex, tileX, tileY, targetX, targetY, totalMen, name));
        }

        return new AvatarStatePacket(playerId, factionIndex, popPerRace, credits, stockpileSurplus, stances, armies);
    }
}
