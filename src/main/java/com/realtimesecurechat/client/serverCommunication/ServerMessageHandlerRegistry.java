package com.realtimesecurechat.client.serverCommunication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimesecurechat.client.peerCommunication.PeerSocketManager;
import com.realtimesecurechat.client.utils.Crypto;
import com.realtimesecurechat.client.UI.ConnectionRequestDialog;
import javafx.application.Platform;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ServerMessageHandlerRegistry {
    private final Map<String, BiConsumer<JsonNode, WebSocketClientToServer>> handlers = new HashMap<>();
    ObjectMapper objectMapper = new ObjectMapper();
    private final KeyPair keyPair;
    private ClientToServerMessagesManager clientToServerMessagesManager;

    public ServerMessageHandlerRegistry(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public void setClientToServerMessagesManager(ClientToServerMessagesManager clientToServerMessagesManager) {
        this.clientToServerMessagesManager = clientToServerMessagesManager;
    }

    public void registerHandlers(WebSocketClientToServer client, PeerSocketManager peerSocketManager) {
        handlers.put("connectionRequest", (json, wsClient) -> handleConnectionRequest(json, peerSocketManager));
        handlers.put("Approval", (json, wsClient) -> handleApproval(json, peerSocketManager));
        handlers.put("Error", (json, wsClient) -> System.err.println("Error: " + json.get("message").asText()));
        System.out.println("Handlers registered");
        handlers.put("Confirmation", (json, wsClient) -> System.out.println("Server confirmation: " + json.get("message").asText()));
    }

    public void handleMessage(String messageType, JsonNode jsonMessage) {
        if (handlers.containsKey(messageType)) {
            handlers.get(messageType).accept(jsonMessage, null);
        } else {
            System.err.println("Unhandled message type: " + messageType);
        }
    }

    /*
        * Handle a connection request from another user
        * JSON format:
        * {
        *  "messageType": "connectionRequest",
        *  "fromUserId": "username",
        *  "requesterPublicKey": "publicKey"
        * }
     */
    private void handleConnectionRequest(JsonNode json, PeerSocketManager peerSocketManager) {
        String userId = json.get("fromUserId").asText();
        String publicKeyStr = json.get("requesterPublicKey").asText();

        // Use a lock to wait for the JavaFX dialog result
        final Object lock = new Object();
        final ConnectionRequestDialog.UserResponse[] userResponse = new ConnectionRequestDialog.UserResponse[1];

        // Initialize the JavaFX platform if not already started
        Platform.startup(() -> {});

        // Ensure the dialog is shown on the JavaFX Application Thread
        javafx.application.Platform.runLater(() -> {
            ConnectionRequestDialog dialog = new ConnectionRequestDialog(userId);
            userResponse[0] = dialog.showDialog();

            // Notify the main thread that the dialog is complete
            synchronized (lock) {
                lock.notify();
            }
        });

        // Wait for the dialog to complete
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Process the result after the dialog is complete
        switch (userResponse[0]) {
            case ACCEPT:
                System.out.println("Connection request accepted for: " + userId);
                peerSocketManager.addIncomingConnectionRequest(userId, Crypto.decodeKey(publicKeyStr));
                clientToServerMessagesManager.approveConnection(userId);
                break;

            case REJECT:
                System.out.println("Connection request rejected for: " + userId);
                clientToServerMessagesManager.rejectConnection(userId);
                break;

            case NO_RESPONSE:
                System.out.println("No response for connection request from: " + userId);
                break;
        }
    }

    /*
        * Handle an approval message from the server
        * JSON format:
        * {
        *  "messageType": "Approval",
        *  "user": "username
        *  "connectionDetails": "encryptedConnectionDetails",
        *  "publicKey": "publicKey"
        * }
     */
    private void handleApproval(JsonNode json, PeerSocketManager peerSocketManager) {
        try {
            String userId = json.get("user").asText();
            String connectionDetails = Crypto.decryptMessage(json.get("connectionDetails").asText(), keyPair.getPrivate());
            String publicKeyStr = json.get("publicKey").asText();
            System.out.println("Received approval from: " + userId);
            System.out.println("Connection details: " + connectionDetails);
            peerSocketManager.connectToPeer(userId, connectionDetails, publicKeyStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JsonNode decryptAndParseMessage(String encryptedMessage) throws Exception {
        String decryptedMessage = Crypto.decryptMessage(encryptedMessage, keyPair.getPrivate());
        return objectMapper.readTree(decryptedMessage);
    }
}
