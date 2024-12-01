package com.realtimesecurechat.server;

import org.glassfish.tyrus.server.Server;
import java.util.HashMap;
import java.util.Map;

public class ServerLauncher {

    public static void main(String[] args) {
        // Server properties
        Map<String, Object> serverProperties = new HashMap<>();

        // Initialize server
        Server server = new Server("localhost", 8080, "/ws", serverProperties, WebSocketServer.class);

        try {
            server.start();
            System.out.println("WebSocket server started at ws://localhost:8080/ws/chat");
            // Keep the server running
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            server.stop();
        }
    }
}
