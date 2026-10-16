package coop.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP Socket implementation of CoopTransport.
 * Designed for local dual-instance testing (-Dcoop.testRole=HOST/CLIENT)
 * and direct IP / LAN multiplayer without Steam dependencies.
 */
public class TcpSocketTransport implements CoopTransport {

    private static final int MAX_PACKET_SIZE = 16 * 1024 * 1024; // 16 MB max frame size

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean isHost = new AtomicBoolean(false);
    private final Queue<byte[]> incomingQueue = new ConcurrentLinkedQueue<byte[]>();
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<ClientHandler>());

    private ServerSocket serverSocket;
    private Thread serverAcceptThread;

    // Client-mode fields
    private Socket clientSocket;
    private DataOutputStream clientOut;
    private DataInputStream clientIn;
    private Thread clientReadThread;

    @Override
    public synchronized void startHost(int port) throws IOException {
        close();
        isHost.set(true);
        running.set(true);

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port));

        serverAcceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running.get() && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setTcpNoDelay(true);
                        socket.setKeepAlive(true);
                        ClientHandler handler = new ClientHandler(socket);
                        clients.add(handler);
                        handler.start();
                    } catch (SocketException e) {
                        // Expected when serverSocket is closed
                        break;
                    } catch (IOException e) {
                        if (running.get()) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }, "Coop-TCP-Acceptor");
        serverAcceptThread.setDaemon(true);
        serverAcceptThread.start();
    }

    @Override
    public synchronized void connect(String address, int port) throws IOException {
        close();
        isHost.set(false);
        running.set(true);

        clientSocket = new Socket();
        clientSocket.setTcpNoDelay(true);
        clientSocket.setKeepAlive(true);
        clientSocket.connect(new InetSocketAddress(address, port), 10000);

        clientOut = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
        clientIn = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));

        clientReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop(clientIn);
            }
        }, "Coop-TCP-ClientReader");
        clientReadThread.setDaemon(true);
        clientReadThread.start();
    }

    @Override
    public synchronized void sendReliable(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return;
        }

        if (isHost.get()) {
            synchronized (clients) {
                List<ClientHandler> dead = new ArrayList<ClientHandler>();
                for (ClientHandler client : clients) {
                    try {
                        client.send(data);
                    } catch (IOException e) {
                        dead.add(client);
                    }
                }
                for (ClientHandler client : dead) {
                    client.close();
                    clients.remove(client);
                }
            }
        } else {
            if (clientOut != null && clientSocket != null && clientSocket.isConnected()) {
                synchronized (clientOut) {
                    clientOut.writeInt(data.length);
                    clientOut.write(data);
                    clientOut.flush();
                }
            } else {
                throw new IOException("Client socket is not connected.");
            }
        }
    }

    @Override
    public void sendUnreliable(byte[] data) throws IOException {
        // TCP does not have unreliable transport; send over the same stream
        sendReliable(data);
    }

    @Override
    public byte[] pollIncoming() {
        return incomingQueue.poll();
    }

    @Override
    public boolean isConnected() {
        if (!running.get()) {
            return false;
        }
        if (isHost.get()) {
            return !clients.isEmpty();
        } else {
            return clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed();
        }
    }

    @Override
    public boolean isHost() {
        return isHost.get();
    }

    @Override
    public void update() {
        // TCP operates on background worker threads; no periodic native poll needed.
    }

    @Override
    public synchronized void close() {
        running.set(false);

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
            serverSocket = null;
        }

        if (serverAcceptThread != null) {
            serverAcceptThread.interrupt();
            serverAcceptThread = null;
        }

        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();
        }

        if (clientSocket != null) {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            clientSocket = null;
        }

        if (clientReadThread != null) {
            clientReadThread.interrupt();
            clientReadThread = null;
        }

        clientOut = null;
        clientIn = null;
        incomingQueue.clear();
    }

    private void readLoop(DataInputStream in) {
        try {
            while (running.get()) {
                int length = in.readInt();
                if (length < 0 || length > MAX_PACKET_SIZE) {
                    throw new IOException("Invalid packet length received: " + length);
                }
                byte[] payload = new byte[length];
                in.readFully(payload);
                incomingQueue.offer(payload);
            }
        } catch (SocketException e) {
            // Connection closed
        } catch (IOException e) {
            if (running.get()) {
                System.err.println("[CoopTransport] Connection read error: " + e.getMessage());
            }
        }
    }

    /**
     * Handles an individual client connection on the host side.
     */
    private class ClientHandler {
        private final Socket socket;
        private final DataOutputStream out;
        private final DataInputStream in;
        private final Thread readThread;

        ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            this.readThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    readLoop(in);
                }
            }, "Coop-Host-ClientReader-" + socket.getPort());
            this.readThread.setDaemon(true);
        }

        void start() {
            this.readThread.start();
        }

        void send(byte[] data) throws IOException {
            synchronized (out) {
                out.writeInt(data.length);
                out.write(data);
                out.flush();
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {}
            readThread.interrupt();
        }
    }
}
