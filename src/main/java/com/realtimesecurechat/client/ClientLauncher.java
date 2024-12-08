package com.realtimesecurechat.client;

import com.realtimesecurechat.client.peerCommunication.PeerSocketManager;
import com.realtimesecurechat.client.serverCommunication.ClientToServerMessagesManager;
import com.realtimesecurechat.client.serverCommunication.ServerMessageHandlerRegistry;
import com.realtimesecurechat.client.serverCommunication.WebSocketClientToServer;
import com.realtimesecurechat.client.UI.ChatAppUI;
import com.realtimesecurechat.client.UI.InitialDialogBox;

import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class ClientLauncher {

    public static void main(String[] args) {
        try {
            // Generate KeyPair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256);
            KeyPair keyPair = keyGen.generateKeyPair();

            // Initialize PeerSocketManager
            int peerPort = 9000; // Example port for peer communication
            PeerSocketManager peerSocketManager = new PeerSocketManager(peerPort, keyPair);
            peerSocketManager.startServer();

            // Initialize ServerMessageHandlerRegistry
            ServerMessageHandlerRegistry serverMessageHandlerRegistry = new ServerMessageHandlerRegistry(keyPair);

            // Initialize WebSocketClient
            URI serverURI = new URI("ws://localhost:8080/ws/chat"); // Example WebSocket server URI
            WebSocketClientToServer webSocketClientToServer = new WebSocketClientToServer(serverURI, serverMessageHandlerRegistry, keyPair);
            serverMessageHandlerRegistry.registerHandlers(webSocketClientToServer, peerSocketManager);

            // Prompt user for userId
            InitialDialogBox dialog = new InitialDialogBox();
            if (!dialog.showDialog()) {
                System.out.println("User canceled registration.");
                return;
            }
            String userId = dialog.getUserId();

            // Perform registration
            ClientToServerMessagesManager clientToServerMessagesManager = new ClientToServerMessagesManager(peerSocketManager, webSocketClientToServer, keyPair);
            serverMessageHandlerRegistry.setClientToServerMessagesManager(clientToServerMessagesManager);
            clientToServerMessagesManager.performRegistration(userId);

            // Launch UI
            ChatAppUI appUI = new ChatAppUI(webSocketClientToServer, peerSocketManager, clientToServerMessagesManager);
            peerSocketManager.setConnectionListener(appUI::onConnectionEstablished);

            System.out.println("Client setup complete!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}