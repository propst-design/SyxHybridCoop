package coop.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * JUnit 5 test verifying loopback TCP transfer of a 5 MB binary payload
 * split across 32 KB CRC32-checked chunks.
 */
public class SaveStreamerTest {

    private TcpSocketTransport server;
    private TcpSocketTransport client;
    private Path tempDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("coop_stream_test_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
        if (tempDir != null && Files.exists(tempDir)) {
            // Delete temp test directory
            File[] files = tempDir.toFile().listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    public void testLoopback5MbSaveTransfer() throws Exception {
        int testPort = findFreePort();
        int payloadSize = 5 * 1024 * 1024; // 5 MB

        // 1. Generate 5 MB source file with pseudo-random content
        Path sourceFile = tempDir.resolve("original_save.bin");
        byte[] expectedBytes = new byte[payloadSize];
        Random rnd = new Random(42);
        rnd.nextBytes(expectedBytes);

        try (FileOutputStream fos = new FileOutputStream(sourceFile.toFile())) {
            fos.write(expectedBytes);
        }

        assertEquals(payloadSize, Files.size(sourceFile), "Source file must be exactly 5 MB");

        // 2. Start TCP server and client
        server = new TcpSocketTransport();
        server.startHost(testPort);

        client = new TcpSocketTransport();
        client.connect("127.0.0.1", testPort);

        // Wait for connection to establish
        long deadline = System.currentTimeMillis() + 3000;
        while ((!server.isConnected() || !client.isConnected()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(server.isConnected(), "Server must have accepted client connection");
        assertTrue(client.isConnected(), "Client must be connected to server");

        // 3. Setup client receiver
        Path destinationFile = tempDir.resolve("received_save.bin");
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Path> receivedPathRef = new AtomicReference<Path>();
        final AtomicReference<String> errorRef = new AtomicReference<String>();

        SaveStreamer.Receiver receiver = new SaveStreamer.Receiver(destinationFile, new SaveStreamer.SaveTransferCallback() {
            @Override
            public void onSaveReceived(Path savedFile) {
                receivedPathRef.set(savedFile);
                latch.countDown();
            }

            @Override
            public void onError(String message, Throwable cause) {
                errorRef.set(message);
                latch.countDown();
            }
        });

        // 4. Background thread to poll packets on client
        Thread clientWorker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (latch.getCount() > 0) {
                    byte[] packet = client.pollIncoming();
                    if (packet != null) {
                        receiver.handlePacket(packet);
                    } else {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
        });
        clientWorker.start();

        // 5. Host streams the 5 MB file
        SaveStreamer.streamFile(sourceFile, server);

        // 6. Await completion (max 10 seconds)
        boolean completed = latch.await(10, TimeUnit.SECONDS);
        clientWorker.interrupt();

        assertTrue(completed, "Save transfer must complete within 10 seconds");
        assertEquals(null, errorRef.get(), "Transfer should not report any error");

        Path receivedPath = receivedPathRef.get();
        assertNotNull(receivedPath, "Received file path must not be null");
        assertTrue(Files.exists(receivedPath), "Received file must exist on disk");
        assertEquals(payloadSize, Files.size(receivedPath), "Received file must match original 5 MB size");

        // 7. Verify byte-by-byte equality
        byte[] actualBytes = Files.readAllBytes(receivedPath);
        assertArrayEquals(expectedBytes, actualBytes, "Received byte content must match original byte content exactly");
    }
}
