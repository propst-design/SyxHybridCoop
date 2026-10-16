package coop.battle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Binary packets coordinating live tactical battle bridging in Mode B -> Mode A.
 */
public class BattlePacket {

    public static final byte TYPE_BATTLE_INIT  = 0x30;
    public static final byte TYPE_BATTLE_ORDER = 0x31;
    public static final byte TYPE_BATTLE_EXIT  = 0x32;

    public final byte type;

    // For TYPE_BATTLE_INIT
    public int attackerFaction;
    public int defenderFaction;
    public int tileX;
    public int tileY;

    // For TYPE_BATTLE_ORDER
    public short divIndex;
    public int destX;
    public int destY;
    public int dir;
    public boolean mustering;

    // For TYPE_BATTLE_EXIT
    public int resultCode;
    public int playerLosses;
    public int enemyLosses;

    public static BattlePacket createInit(int attackerFaction, int defenderFaction, int tileX, int tileY) {
        BattlePacket p = new BattlePacket(TYPE_BATTLE_INIT);
        p.attackerFaction = attackerFaction;
        p.defenderFaction = defenderFaction;
        p.tileX = tileX;
        p.tileY = tileY;
        return p;
    }

    public static BattlePacket createOrder(short divIndex, int destX, int destY, int dir, boolean mustering) {
        BattlePacket p = new BattlePacket(TYPE_BATTLE_ORDER);
        p.divIndex = divIndex;
        p.destX = destX;
        p.destY = destY;
        p.dir = dir;
        p.mustering = mustering;
        return p;
    }

    public static BattlePacket createExit(int resultCode, int playerLosses, int enemyLosses) {
        BattlePacket p = new BattlePacket(TYPE_BATTLE_EXIT);
        p.resultCode = resultCode;
        p.playerLosses = playerLosses;
        p.enemyLosses = enemyLosses;
        return p;
    }

    public BattlePacket(byte type) {
        this.type = type;
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeByte(type);
        if (type == TYPE_BATTLE_INIT) {
            dos.writeInt(attackerFaction);
            dos.writeInt(defenderFaction);
            dos.writeInt(tileX);
            dos.writeInt(tileY);
        } else if (type == TYPE_BATTLE_ORDER) {
            dos.writeShort(divIndex);
            dos.writeInt(destX);
            dos.writeInt(destY);
            dos.writeInt(dir);
            dos.writeBoolean(mustering);
        } else if (type == TYPE_BATTLE_EXIT) {
            dos.writeInt(resultCode);
            dos.writeInt(playerLosses);
            dos.writeInt(enemyLosses);
        }

        dos.flush();
        return baos.toByteArray();
    }

    public static BattlePacket parse(byte[] data) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais);

        byte type = dis.readByte();
        BattlePacket p = new BattlePacket(type);

        if (type == TYPE_BATTLE_INIT) {
            p.attackerFaction = dis.readInt();
            p.defenderFaction = dis.readInt();
            p.tileX = dis.readInt();
            p.tileY = dis.readInt();
        } else if (type == TYPE_BATTLE_ORDER) {
            p.divIndex = dis.readShort();
            p.destX = dis.readInt();
            p.destY = dis.readInt();
            p.dir = dis.readInt();
            p.mustering = dis.readBoolean();
        } else if (type == TYPE_BATTLE_EXIT) {
            p.resultCode = dis.readInt();
            p.playerLosses = dis.readInt();
            p.enemyLosses = dis.readInt();
        } else {
            throw new IOException("Unknown BattlePacket type: 0x" + Integer.toHexString(type & 0xFF));
        }

        return p;
    }
}
