package coop.cmd;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import snake2d.util.rnd.RND;

/**
 * Headless JUnit 5 tests verifying simulation RNG isolation and lockstep command ordering.
 */
public class LockstepDeterminismTest {

    @BeforeEach
    public void setUp() {
        CoopLockstepCoordinator.reset();
        RND.inSimStep = false;
        RND.simThread = null;
    }

    @AfterEach
    public void tearDown() {
        CoopLockstepCoordinator.reset();
        RND.inSimStep = false;
        RND.simThread = null;
    }

    @Test
    public void testRngSplittingPreventsUiDesync() {
        int initialSeed = 99999;

        // 1. Run simulation steps and capture sequence A
        RND.setSeed(initialSeed);
        RND.inSimStep = true;
        RND.simThread = Thread.currentThread();

        int[] simSequenceA = new int[100];
        for (int i = 0; i < 100; i++) {
            simSequenceA[i] = RND.rInt();
        }

        // 2. Reset seed to initialSeed, but inject 5,000 arbitrary UI/render calls with inSimStep = false
        RND.setSeed(initialSeed);
        RND.inSimStep = false; // UI context (tooltips, sound, weather render)

        for (int i = 0; i < 5000; i++) {
            RND.rInt();
            RND.rFloat();
            RND.rBoolean();
            RND.rLong();
        }

        // 3. Now enter simulation step again and capture sequence B
        RND.inSimStep = true;
        int[] simSequenceB = new int[100];
        for (int i = 0; i < 100; i++) {
            simSequenceB[i] = RND.rInt();
        }

        // 4. Assert that sequence B is 100% identical to sequence A despite 5,000 UI random calls
        assertArrayEquals(simSequenceA, simSequenceB, "Simulation RNG must be completely immune to UI / rendering RNG calls");
    }

    @Test
    public void testLockstepCommandDeterministicOrdering() {
        final List<String> executionLog = new ArrayList<String>();
        int targetTick = 42;

        // Player 1 sends cmd with seq 2
        CoopLockstepCoordinator.scheduleCommand(new CommandPacket(CommandPacket.TYPE_SPEED, targetTick, 1, 2) {
            @Override
            public void execute() {
                executionLog.add("P1_S2");
            }
            @Override
            public void writePayload(java.io.DataOutputStream out) {}
        });

        // Player 0 sends cmd with seq 1
        CoopLockstepCoordinator.scheduleCommand(new CommandPacket(CommandPacket.TYPE_SPEED, targetTick, 0, 1) {
            @Override
            public void execute() {
                executionLog.add("P0_S1");
            }
            @Override
            public void writePayload(java.io.DataOutputStream out) {}
        });

        // Player 0 sends cmd with seq 2
        CoopLockstepCoordinator.scheduleCommand(new CommandPacket(CommandPacket.TYPE_SPEED, targetTick, 0, 2) {
            @Override
            public void execute() {
                executionLog.add("P0_S2");
            }
            @Override
            public void writePayload(java.io.DataOutputStream out) {}
        });

        // Player 1 sends cmd with seq 1
        CoopLockstepCoordinator.scheduleCommand(new CommandPacket(CommandPacket.TYPE_SPEED, targetTick, 1, 1) {
            @Override
            public void execute() {
                executionLog.add("P1_S1");
            }
            @Override
            public void writePayload(java.io.DataOutputStream out) {}
        });

        // Execute preTick for targetTick
        CoopLockstepCoordinator.preTick(targetTick);

        // Verify sorted order: P0_S1 -> P0_S2 -> P1_S1 -> P1_S2
        assertEquals(4, executionLog.size());
        assertEquals("P0_S1", executionLog.get(0));
        assertEquals("P0_S2", executionLog.get(1));
        assertEquals("P1_S1", executionLog.get(2));
        assertEquals("P1_S2", executionLog.get(3));
    }

    @Test
    public void testLockstepTickBarrier() {
        // Without lockstep active, can advance freely
        assertTrue(CoopLockstepCoordinator.canAdvanceTick(10));
    }
}
