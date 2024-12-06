package com.realtimesecurechat.client;

import java.net.URI;
import java.util.Scanner;

public class ClientLauncher {
    public static void main(String[] args) {
        // Initialize WebSocket client without SSL
        WebSocketClient client = new WebSocketClient(URI.create("ws://localhost:8080/ws/chat"), 9000);
        new UserIdInputUI(client); // Start with the User ID Input UI
        // Add a small delay to allow time for the connection to be established
        try {
            Thread.sleep(2000);  // 2-second delay
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}