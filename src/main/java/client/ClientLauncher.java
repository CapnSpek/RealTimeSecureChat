package client;

import java.net.URI;
import java.util.Scanner;

public class ClientLauncher {
    public static void main(String[] args) {
        // Initialize WebSocket client without SSL
        WebSocketClient client = new WebSocketClient(URI.create("ws://localhost:8080/ws/chat"), "localhost", 9000);

        // Add a small delay to allow time for the connection to be established
        try {
            Thread.sleep(2000);  // 2-second delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Input target session ID for the client you want to connect to
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter target client session ID to connect to: ");
        String targetSessionId = scanner.nextLine();

        // Request connection with the target client by sending the session ID
        client.requestConnection(targetSessionId);
    }
}