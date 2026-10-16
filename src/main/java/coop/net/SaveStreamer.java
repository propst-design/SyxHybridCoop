package coop.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.BitSet;
import java.util.zip.CRC32;

/**
 * Handles streaming of game save files between Host and Client in 32 KB CRC32-checked chunks.
 */
public class SaveStreamer {

    public static final int CHUNK_SIZE = 32 * 1024; // 32 KB

    public static final byte TYPE_SAVE_START   = 0x53; // 'S'
    public static final byte TYPE_SAVE_CHUNK   = 0x43; // 'C'
    public static final byte TYPE_SAVE_END     = 0x45; // 'E'
    public static final byte TYPE_SAVE_REQUEST = 0x52; // 'R'

    public interface SaveTransferCallback {
        void onSaveReceived(Path savedFile);
        void onError(String message, Throwable cause);
    }

    /**
     * Reads a file and streams it over the transport in 32 KB CRC32-checked chunks.
     */
    public static void streamFile(Path filePath, CoopTransport transport) throws IOException {
        File file = filePath.toFile();
        long totalSize = file.length();
        int totalChunks = (int) ((totalSize + CHUNK_SIZE - 1) / CHUNK_SIZE);

        // Compute total file CRC32
        CRC32 fileCrc = new CRC32();
        byte[] readBuf = new byte[CHUNK_SIZE];
        try (InputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(readBuf)) != -1) {
                fileCrc.update(readBuf, 0, read);
            }
        }
        long totalCrcValue = fileCrc.getValue();

        // 1. Send SAVE_START packet
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeByte(TYPE_SAVE_START);
        dos.writeLong(totalSize);
        dos.writeInt(totalChunks);
        dos.writeLong(totalCrcValue);
        dos.writeUTF(file.getName());
        dos.flush();
        transport.sendReliable(baos.toByteArray());

        // 2. Send SAVE_CHUNK packets
        try (InputStream in = new FileInputStream(file)) {
            CRC32 chunkCrc = new CRC32();
            for (int i = 0; i < totalChunks; i++) {
                int read = in.read(readBuf);
                if (read <= 0) break;

                chunkCrc.reset();
                chunkCrc.update(readBuf, 0, read);

                baos.reset();
                dos = new DataOutputStream(baos);
                dos.writeByte(TYPE_SAVE_CHUNK);
                dos.writeInt(i);
                dos.writeInt(read);
                dos.writeLong(chunkCrc.getValue());
                dos.write(readBuf, 0, read);
                dos.flush();

                transport.sendReliable(baos.toByteArray());
            }
        }

        // 3. Send SAVE_END packet
        baos.reset();
        dos = new DataOutputStream(baos);
        dos.writeByte(TYPE_SAVE_END);
        dos.writeLong(totalCrcValue);
        dos.flush();
        transport.sendReliable(baos.toByteArray());
    }

    /**
     * Receiver state machine that assembles incoming chunks into a verified save file.
     */
    public static class Receiver {
        private final Path destinationPath;
        private final SaveTransferCallback callback;
        private Path tempPath;
        private RandomAccessFile raf;

        private long expectedTotalSize = -1;
        private int expectedChunks = -1;
        private long expectedFileCrc = -1;
        private BitSet receivedChunks;
        private int receivedCount = 0;

        public Receiver(Path destinationPath, SaveTransferCallback callback) {
            this.destinationPath = destinationPath;
            this.callback = callback;
        }

        public synchronized boolean handlePacket(byte[] packet) {
            if (packet == null || packet.length == 0) {
                return false;
            }

            byte type = packet[0];
            try {
                if (type == TYPE_SAVE_START) {
                    handleSaveStart(packet);
                    return true;
                } else if (type == TYPE_SAVE_CHUNK) {
                    handleSaveChunk(packet);
                    return true;
                } else if (type == TYPE_SAVE_END) {
                    handleSaveEnd(packet);
                    return true;
                }
            } catch (Exception e) {
                cleanupTempFile();
                if (callback != null) {
                    callback.onError("Failed processing save stream packet: " + e.getMessage(), e);
                }
            }
            return false;
        }

        private void handleSaveStart(byte[] packet) throws IOException {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet, 1, packet.length - 1));
            this.expectedTotalSize = dis.readLong();
            this.expectedChunks = dis.readInt();
            this.expectedFileCrc = dis.readLong();
            String fileName = dis.readUTF();

            cleanupTempFile();

            Path parent = destinationPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            this.tempPath = destinationPath.resolveSibling(destinationPath.getFileName().toString() + ".tmp");
            if (Files.exists(tempPath)) {
                Files.delete(tempPath);
            }

            this.raf = new RandomAccessFile(tempPath.toFile(), "rw");
            this.raf.setLength(expectedTotalSize);
            this.receivedChunks = new BitSet(expectedChunks);
            this.receivedCount = 0;
        }

        private void handleSaveChunk(byte[] packet) throws IOException {
            if (raf == null) {
                throw new IOException("Received chunk before SAVE_START.");
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet, 1, packet.length - 1));
            int chunkIndex = dis.readInt();
            int chunkSize = dis.readInt();
            long chunkCrc = dis.readLong();

            byte[] chunkData = new byte[chunkSize];
            dis.readFully(chunkData);

            // Verify chunk CRC32
            CRC32 crc = new CRC32();
            crc.update(chunkData, 0, chunkSize);
            if (crc.getValue() != chunkCrc) {
                throw new IOException("CRC32 mismatch on chunk " + chunkIndex + ": expected " + chunkCrc + ", got " + crc.getValue());
            }

            long offset = (long) chunkIndex * CHUNK_SIZE;
            raf.seek(offset);
            raf.write(chunkData, 0, chunkSize);

            if (!receivedChunks.get(chunkIndex)) {
                receivedChunks.set(chunkIndex);
                receivedCount++;
            }
        }

        private void handleSaveEnd(byte[] packet) throws IOException {
            if (raf == null) {
                throw new IOException("Received SAVE_END before SAVE_START.");
            }

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(packet, 1, packet.length - 1));
            long finalCrc = dis.readLong();

            raf.close();
            raf = null;

            if (receivedCount < expectedChunks) {
                throw new IOException("Incomplete save transfer: received " + receivedCount + "/" + expectedChunks + " chunks.");
            }

            // Verify whole-file CRC32
            CRC32 fileCrc = new CRC32();
            byte[] readBuf = new byte[CHUNK_SIZE];
            try (InputStream in = new FileInputStream(tempPath.toFile())) {
                int read;
                while ((read = in.read(readBuf)) != -1) {
                    fileCrc.update(readBuf, 0, read);
                }
            }

            if (fileCrc.getValue() != expectedFileCrc || fileCrc.getValue() != finalCrc) {
                throw new IOException("Overall save file CRC32 mismatch! Expected: " + expectedFileCrc + ", Actual: " + fileCrc.getValue());
            }

            // Atomically replace destination save file
            Files.move(tempPath, destinationPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            if (callback != null) {
                callback.onSaveReceived(destinationPath);
            }
        }

        public int getReceivedCount() {
            return receivedCount;
        }

        public int getExpectedChunks() {
            return expectedChunks;
        }

        public int getProgressPercent() {
            if (expectedChunks <= 0) return 0;
            return (int) ((receivedCount * 100L) / expectedChunks);
        }

        public void cleanupTempFile() {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {}
                raf = null;
            }
            if (tempPath != null) {
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException ignored) {}
                tempPath = null;
            }
        }
    }

    /**
     * In-game helper: On Host, pauses game speed, saves "__coop_sync", and streams save file to connected peers.
     */
    public static void hostSaveAndStream(final CoopTransport transport) {
        try {
            // Pause simulation deterministically
            if (game.GAME.SPEED != null) {
                game.GAME.SPEED.speedSet(0);
            }
            final Path savePath = game.GAME.saver().save("__coop_sync", true);
            if (savePath != null && Files.exists(savePath)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.out.println("[SaveStreamer] Streaming save file to peers: " + savePath);
                            streamFile(savePath, transport);
                            System.out.println("[SaveStreamer] Save streaming complete.");
                        } catch (IOException e) {
                            System.err.println("[SaveStreamer] Failed streaming save file: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }, "Coop-SaveStreamer-Worker").start();
            } else {
                System.err.println("[SaveStreamer] Failed to generate __coop_sync save file.");
            }
        } catch (Throwable t) {
            System.err.println("[SaveStreamer] Host save and stream failed: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * In-game helper: Creates receiver pointing to client sync save file.
     */
    public static Receiver createClientReceiver(final SaveTransferCallback onComplete) {
        Path clientSavePath = init.paths.PATHS.local().save().get().resolve("__coop_client_sync.save");
        return new Receiver(clientSavePath, new SaveTransferCallback() {
            @Override
            public void onSaveReceived(Path savedFile) {
                System.out.println("[SaveStreamer] Received verified save file: " + savedFile);
                if (onComplete != null) {
                    onComplete.onSaveReceived(savedFile);
                }
                // Trigger game loader to transition into synchronized state
                try {
                    new game.save.GameLoader(savedFile).set();
                } catch (Throwable t) {
                    System.err.println("[SaveStreamer] Failed to load synchronized game state: " + t.getMessage());
                    t.printStackTrace();
                }
            }

            @Override
            public void onError(String message, Throwable cause) {
                System.err.println("[SaveStreamer] Client receive error: " + message);
                if (cause != null) {
                    cause.printStackTrace();
                }
                if (onComplete != null) {
                    onComplete.onError(message, cause);
                }
            }
        });
    }
}
