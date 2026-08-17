package test4;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import game.BoardUtils;

public class TurnListener implements Runnable {
    
    private int port = 5024;
    private static volatile Boolean isWhitsTurn = Boolean.TRUE;

    public Boolean getTurn() { return isWhitsTurn; }

    @Override
    public void run() {
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Turn listener on port " + port);
            while (true) {
                try (Socket s = server.accept();
                     ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    Object obj = in.readObject();
                    if (obj instanceof Boolean) {
                        isWhitsTurn = (Boolean) obj;            // so getTurn() reflects the real turn
                        BoardUtils.isWhiteTurn = (Boolean) obj;
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
