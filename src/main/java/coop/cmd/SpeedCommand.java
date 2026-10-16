package coop.cmd;

import java.io.DataOutputStream;
import java.io.IOException;

import game.GAME;

/**
 * Synchronized game speed / pause command executed at targetTick.
 */
public class SpeedCommand extends CommandPacket {

    private final double targetSpeed;

    public SpeedCommand(int targetTick, int playerId, long sequence, double targetSpeed) {
        super(TYPE_SPEED, targetTick, playerId, sequence);
        this.targetSpeed = targetSpeed;
    }

    public double getTargetSpeed() {
        return targetSpeed;
    }

    @Override
    public void execute() {
        if (GAME.SPEED != null) {
            GAME.SPEED.speedSet(targetSpeed);
        }
    }

    @Override
    public void writePayload(DataOutputStream out) throws IOException {
        out.writeDouble(targetSpeed);
    }
}
