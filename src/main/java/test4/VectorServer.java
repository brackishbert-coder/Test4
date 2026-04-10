package test4;



import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * VectorServer — Runnable implementation.
 * Streams vectors to all connected clients using a safe,
 * lossless length-prefixed binary protocol.
 */
public class VectorServer implements Runnable {

    private final int port;
    private final List<DataOutputStream> clients = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    public VectorServer(int port) {
        this.port = port;
    }

    public VectorServer() {
        this(5020);
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            System.out.println("VectorServer listening on port " + port);

            // 🧵 Thread to accept new clients
            Thread acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        socket.setTcpNoDelay(true);
                        socket.setKeepAlive(true);

                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        clients.add(out);

                        System.out.println("Client connected. Total clients: " + clients.size());
                    } catch (IOException e) {
                        if (running)
                            System.err.println("Accept error: " + e.getMessage());
                    }
                }
            });

            acceptThread.setDaemon(true);
            acceptThread.start();

            // MAIN BROADCAST LOOP
            while (running) {
                // Blocks until a vector arrives
                double[] vec = VectorServerQueue.take();
                broadcast(vec);
            }

        } catch (IOException e) {
            System.err.println("VectorServer fatal error: " + e.getMessage());
        }
    }

    /**
     * Broadcast a double[] vector to every connected client.
     * Length-prefixed = never desyncs, never loses alignment.
     */
    private void broadcast(double[] data) {
        for (DataOutputStream out : clients) {
            try {
                // 1) Write length
                out.writeInt(data.length);

                // 2) Write values
                for (double d : data) {
                    out.writeDouble(d);
                }

                // 3) Flush immediately
                out.flush();

            } catch (IOException e) {
                // client died — remove it
                clients.remove(out);
                System.out.println("Client disconnected. Remaining: " + clients.size());
            }
        }
    }

    /**
     * Graceful shutdown if needed.
     */
    public void stopServer() {
        running = false;
        System.out.println("VectorServer stopping...");
    }
}
