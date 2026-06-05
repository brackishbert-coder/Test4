package test4;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import game.tile;

public class TileListener implements Runnable {
    private volatile List<tile> tiles = Collections.emptyList();
    private int port = 5021;

    public List<tile> getTiles() { return tiles; }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Tile listener on port " + port);
            while (true) {
                try (Socket s = server.accept();
                     ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    Object obj = in.readObject();
                    if (obj instanceof List) {
                        tiles = (ArrayList<tile>) obj;
                        
                        
                       // System.out.println("[TileListener] received " + tiles.size() + " tiles");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
