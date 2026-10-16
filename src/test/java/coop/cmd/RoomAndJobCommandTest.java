package coop.cmd;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Headless unit tests verifying serialization, deserialization,
 * and wire framing of RoomCommandPacket and JobCommandPacket.
 */
public class RoomAndJobCommandTest {

    @Test
    public void testRoomCommandPacketSerialization() throws IOException {
        int targetTick = 100;
        int playerId = 1;
        long sequence = 42;
        byte action = RoomCommandPacket.ACTION_CREATE_WITH_DATA;
        int blueprintIndex = 7;
        int upgrade = 2;
        int actionTx = 15;
        int actionTy = 25;

        List<RoomCommandPacket.TileData> tiles = new ArrayList<RoomCommandPacket.TileData>();
        tiles.add(new RoomCommandPacket.TileData(10, 20, 101));
        tiles.add(new RoomCommandPacket.TileData(11, 20, 102));
        tiles.add(new RoomCommandPacket.TileData(12, 20, 103));

        RoomCommandPacket original = new RoomCommandPacket(
                targetTick, playerId, sequence, action,
                blueprintIndex, upgrade, actionTx, actionTy, tiles, null
        );

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Parse via generic CommandPacket.parse
        CommandPacket parsed = CommandPacket.parse(bytes);
        assertTrue(parsed instanceof RoomCommandPacket, "Parsed packet must be instance of RoomCommandPacket");

        RoomCommandPacket roomPkt = (RoomCommandPacket) parsed;
        assertEquals(CommandPacket.TYPE_ROOM, roomPkt.getType());
        assertEquals(targetTick, roomPkt.getTargetTick());
        assertEquals(playerId, roomPkt.getPlayerId());
        assertEquals(sequence, roomPkt.getSequence());
        assertEquals(action, roomPkt.getAction());
        assertEquals(blueprintIndex, roomPkt.getBlueprintIndex());
        assertEquals(upgrade, roomPkt.getUpgrade());

        List<RoomCommandPacket.TileData> readTiles = roomPkt.getTiles();
        assertEquals(3, readTiles.size());
        assertEquals(10, readTiles.get(0).x);
        assertEquals(20, readTiles.get(0).y);
        assertEquals(101, readTiles.get(0).dData);
        assertEquals(12, readTiles.get(2).x);
        assertEquals(20, readTiles.get(2).y);
        assertEquals(103, readTiles.get(2).dData);
    }

    @Test
    public void testJobCommandPacketSerialization() throws IOException {
        int targetTick = 250;
        int playerId = 0;
        long sequence = 88;
        byte toolType = JobCommandPacket.TOOL_JOB;
        int jobIndex = 14;
        int placerTypeIndex = 3;

        List<int[]> coords = new ArrayList<int[]>();
        coords.add(new int[]{50, 60});
        coords.add(new int[]{51, 60});
        coords.add(new int[]{52, 60});
        coords.add(new int[]{53, 60});

        JobCommandPacket original = new JobCommandPacket(
                targetTick, playerId, sequence, toolType,
                jobIndex, placerTypeIndex, coords
        );

        byte[] bytes = original.toBytes();
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        // Parse via generic CommandPacket.parse
        CommandPacket parsed = CommandPacket.parse(bytes);
        assertTrue(parsed instanceof JobCommandPacket, "Parsed packet must be instance of JobCommandPacket");

        JobCommandPacket jobPkt = (JobCommandPacket) parsed;
        assertEquals(CommandPacket.TYPE_JOB, jobPkt.getType());
        assertEquals(targetTick, jobPkt.getTargetTick());
        assertEquals(playerId, jobPkt.getPlayerId());
        assertEquals(sequence, jobPkt.getSequence());
        assertEquals(toolType, jobPkt.getToolType());
        assertEquals(jobIndex, jobPkt.getJobIndex());
        assertEquals(placerTypeIndex, jobPkt.getPlacerTypeIndex());

        List<int[]> readCoords = jobPkt.getCoordinates();
        assertEquals(4, readCoords.size());
        assertEquals(50, readCoords.get(0)[0]);
        assertEquals(60, readCoords.get(0)[1]);
        assertEquals(53, readCoords.get(3)[0]);
        assertEquals(60, readCoords.get(3)[1]);
    }
}
