package coop.cmd;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Tick confirmation and rolling state checksum packet.
 * Used for lockstep tick barriers and desync detection.
 */
public class TickFramePacket extends CommandPacket {

    private final long stateChecksum;

    public TickFramePacket(int tick, int playerId, long sequence, long stateChecksum) {
        super(TYPE_TICK_FRAME, tick, playerId, sequence);
        this.stateChecksum = stateChecksum;
    }

    public long getStateChecksum() {
        return stateChecksum;
    }

    @Override
    public void execute() {
        // Tick frames are processed by CoopLockstepCoordinator barrier
    }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeLong(stateChecksum);
    }
}
