package coop.cmd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Base binary frame for deterministic lockstep commands in Mode A.
 */
public abstract class CommandPacket {

    public static final byte TYPE_TICK_FRAME = 0x01;
    public static final byte TYPE_SPEED      = 0x02;
    public static final byte TYPE_ROOM       = 0x03;
    public static final byte TYPE_JOB        = 0x04;

    protected final byte type;
    protected final int targetTick;
    protected final int playerId;
    protected final long sequence;

    protected CommandPacket(byte type, int targetTick, int playerId, long sequence) {
        this.type = type;
        this.targetTick = targetTick;
        this.playerId = playerId;
        this.sequence = sequence;
    }

    public byte getType() {
        return type;
    }

    public int getTargetTick() {
        return targetTick;
    }

    public int getPlayerId() {
        return playerId;
    }

    public long getSequence() {
        return sequence;
    }

    public abstract void execute();

    public abstract void writePayload(DataOutputStream out) throws IOException;

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(type);
        dos.writeInt(targetTick);
        dos.writeInt(playerId);
        dos.writeLong(sequence);
        writePayload(dos);
        dos.flush();
        return baos.toByteArray();
    }

    public static CommandPacket parse(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 17) {
            return null;
        }
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        byte type = dis.readByte();
        int targetTick = dis.readInt();
        int playerId = dis.readInt();
        long sequence = dis.readLong();

        if (type == TYPE_TICK_FRAME) {
            long checksum = dis.readLong();
            return new TickFramePacket(targetTick, playerId, sequence, checksum);
        } else if (type == TYPE_SPEED) {
            double targetSpeed = dis.readDouble();
            return new SpeedCommand(targetTick, playerId, sequence, targetSpeed);
        } else if (type == TYPE_ROOM) {
            return RoomCommandPacket.readPayload(targetTick, playerId, sequence, dis);
        } else if (type == TYPE_JOB) {
            return JobCommandPacket.readPayload(targetTick, playerId, sequence, dis);
        }
        return null;
    }
}
