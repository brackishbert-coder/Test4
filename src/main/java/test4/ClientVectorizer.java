package test4;



import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.Socket;
import java.util.Arrays;

/**
 * Feature-based vectorizer with fixed 16×16 output.
 * Each of the 256 elements represents local brightness,
 * edge strength, and texture variance from a region of
 * the full webcam image.
 */
public class ClientVectorizer implements Runnable {

    private static final int GRID_W = 16;
    private static final int GRID_H = 16;
    String host = "localhost";
    int port = 5010;
	private double[] receiveds = new double[GRID_W*GRID_H];
    public ClientVectorizer() {
    }

public double[] getFeatureVector() {

    int size = GRID_W * GRID_H;
    double[] out = new double[size];

    // Copy only as much as exists
    int limit = Math.min(receiveds.length, size);
    System.arraycopy(receiveds, 0, out, 0, limit);

    return out; // ALWAYS returns a fresh array
}


	@Override
	public void run() {
        System.out.println("VectorClient waiting for processed vectors...");
        while (true) {
            try {
                Socket socket = new Socket(host, port);
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                System.out.println("Connected to ClientVectorizer!");

                while (true) {
                    try {
                        receiveds = (double[]) in.readObject();
                      //  System.out.println("Received processed vector: " + Arrays.toString(receiveds));
                    } catch (EOFException e) {
                        System.out.println("Server closed connection, reconnecting...");
                        break;
                    }
                }

                socket.close();
                Thread.sleep(10); // wait before reconnect

            } catch (IOException | ClassNotFoundException | InterruptedException e) {
                System.err.println("Connection error: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
		
	}
}
