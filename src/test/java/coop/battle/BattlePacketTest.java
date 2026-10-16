package coop.battle;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import world.battle.spec.BATTLE_RESULT;

import static org.junit.jupiter.api.Assertions.*;

public class BattlePacketTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        CoopBattleBridge.setOpponentHuman(false);
        CoopBattleBridge.setLocalPlayerOpponent(false);
    }

    @Test
    public void testBattleInitPacketRoundtrip() throws IOException {
        BattlePacket init = BattlePacket.createInit(0, 4, 142, 210);
        byte[] bytes = init.toBytes();

        assertNotNull(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_INIT, bytes[0]);

        BattlePacket parsed = BattlePacket.parse(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_INIT, parsed.type);
        assertEquals(0, parsed.attackerFaction);
        assertEquals(4, parsed.defenderFaction);
        assertEquals(142, parsed.tileX);
        assertEquals(210, parsed.tileY);
    }

    @Test
    public void testBattleOrderPacketRoundtrip() throws IOException {
        BattlePacket order = BattlePacket.createOrder((short) 2, 350, 412, 3, true);
        byte[] bytes = order.toBytes();

        assertNotNull(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_ORDER, bytes[0]);

        BattlePacket parsed = BattlePacket.parse(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_ORDER, parsed.type);
        assertEquals(2, parsed.divIndex);
        assertEquals(350, parsed.destX);
        assertEquals(412, parsed.destY);
        assertEquals(3, parsed.dir);
        assertTrue(parsed.mustering);
    }

    @Test
    public void testBattleExitPacketRoundtrip() throws IOException {
        BattlePacket exit = BattlePacket.createExit(BATTLE_RESULT.VICTORY.ordinal(), 42, 115);
        byte[] bytes = exit.toBytes();

        assertNotNull(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_EXIT, bytes[0]);

        BattlePacket parsed = BattlePacket.parse(bytes);
        assertEquals(BattlePacket.TYPE_BATTLE_EXIT, parsed.type);
        assertEquals(BATTLE_RESULT.VICTORY.ordinal(), parsed.resultCode);
        assertEquals(42, parsed.playerLosses);
        assertEquals(115, parsed.enemyLosses);
    }

    @Test
    public void testCoopBattleBridgeState() {
        assertFalse(CoopBattleBridge.isOpponentHuman());
        assertFalse(CoopBattleBridge.isLocalPlayerOpponent());
        assertFalse(CoopBattleBridge.isInLiveBattle());

        CoopBattleBridge.setOpponentHuman(true);
        assertTrue(CoopBattleBridge.isOpponentHuman());

        CoopBattleBridge.setLocalPlayerOpponent(true);
        assertTrue(CoopBattleBridge.isLocalPlayerOpponent());

        CoopBattleBridge.onBattleConcluded(BATTLE_RESULT.VICTORY, 10, 20);
        assertFalse(CoopBattleBridge.isOpponentHuman());
        assertFalse(CoopBattleBridge.isLocalPlayerOpponent());
        assertFalse(CoopBattleBridge.isInLiveBattle());
    }
}
