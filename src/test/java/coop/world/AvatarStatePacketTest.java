package coop.world;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import coop.world.AvatarStatePacket.ArmyData;

import static org.junit.jupiter.api.Assertions.*;

public class AvatarStatePacketTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        CoopWorldRegistry.reset();
    }

    @Test
    public void testAvatarStatePacketRoundtrip() throws IOException {
        int playerId = 1;
        int factionIndex = 5;

        Map<Integer, Integer> popPerRace = new HashMap<Integer, Integer>();
        popPerRace.put(0, 1500);
        popPerRace.put(2, 350);
        popPerRace.put(4, 82);

        double credits = 25420.50;

        Map<Short, Integer> stockpileSurplus = new HashMap<Short, Integer>();
        stockpileSurplus.put((short) 1, 1200);
        stockpileSurplus.put((short) 5, 450);
        stockpileSurplus.put((short) 14, 80);

        byte[] stances = new byte[64];
        stances[0] = 1; // War
        stances[1] = 2; // Trade
        stances[5] = 4; // Allied
        stances[12] = 3; // Pact

        List<ArmyData> armies = new ArrayList<ArmyData>();
        armies.add(new ArmyData((short) 10, 120, 85, 150, 90, 450, "1st Imperial Legion"));
        armies.add(new ArmyData((short) 11, 200, 310, 200, 310, 120, "Royal Guard"));

        AvatarStatePacket packet = new AvatarStatePacket(playerId, factionIndex, popPerRace, credits,
                stockpileSurplus, stances, armies);

        byte[] serialized = packet.toBytes();
        assertNotNull(serialized);
        assertEquals(AvatarStatePacket.TYPE_AVATAR_STATE, serialized[0]);

        AvatarStatePacket parsed = AvatarStatePacket.parse(serialized);
        assertEquals(playerId, parsed.getPlayerId());
        assertEquals(factionIndex, parsed.getFactionIndex());
        assertEquals(credits, parsed.getCredits(), 0.0001);

        // Verify populations
        assertEquals(3, parsed.getPopPerRace().size());
        assertEquals(Integer.valueOf(1500), parsed.getPopPerRace().get(0));
        assertEquals(Integer.valueOf(350), parsed.getPopPerRace().get(2));
        assertEquals(Integer.valueOf(82), parsed.getPopPerRace().get(4));

        // Verify stockpiles
        assertEquals(3, parsed.getStockpileSurplus().size());
        assertEquals(Integer.valueOf(1200), parsed.getStockpileSurplus().get((short) 1));
        assertEquals(Integer.valueOf(450), parsed.getStockpileSurplus().get((short) 5));
        assertEquals(Integer.valueOf(80), parsed.getStockpileSurplus().get((short) 14));

        // Verify stances
        assertEquals(64, parsed.getStances().length);
        assertEquals(1, parsed.getStances()[0]);
        assertEquals(2, parsed.getStances()[1]);
        assertEquals(4, parsed.getStances()[5]);
        assertEquals(3, parsed.getStances()[12]);

        // Verify armies
        assertEquals(2, parsed.getArmies().size());
        ArmyData a1 = parsed.getArmies().get(0);
        assertEquals(10, a1.armyIndex);
        assertEquals(120, a1.tileX);
        assertEquals(85, a1.tileY);
        assertEquals(150, a1.targetX);
        assertEquals(90, a1.targetY);
        assertEquals(450, a1.totalMen);
        assertEquals("1st Imperial Legion", a1.name);

        ArmyData a2 = parsed.getArmies().get(1);
        assertEquals(11, a2.armyIndex);
        assertEquals(200, a2.tileX);
        assertEquals(310, a2.tileY);
        assertEquals(120, a2.totalMen);
        assertEquals("Royal Guard", a2.name);
    }

    @Test
    public void testCoopWorldRegistryBindings() {
        assertFalse(CoopWorldRegistry.isPlayerAvatar(3));
        assertEquals(-1, CoopWorldRegistry.getFactionForPlayer(1));
        assertEquals(-1, CoopWorldRegistry.getPlayerForFaction(3));

        CoopWorldRegistry.bindPlayerAvatar(1, 3);
        assertTrue(CoopWorldRegistry.isPlayerAvatar(3));
        assertFalse(CoopWorldRegistry.isPlayerAvatar(2));
        assertEquals(3, CoopWorldRegistry.getFactionForPlayer(1));
        assertEquals(1, CoopWorldRegistry.getPlayerForFaction(3));

        CoopWorldRegistry.unbindPlayerAvatar(1);
        assertFalse(CoopWorldRegistry.isPlayerAvatar(3));
        assertEquals(-1, CoopWorldRegistry.getFactionForPlayer(1));
        assertEquals(-1, CoopWorldRegistry.getPlayerForFaction(3));
    }
}
