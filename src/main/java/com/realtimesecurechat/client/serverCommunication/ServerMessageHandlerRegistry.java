package com.realtimesecurechat.client.serverCommunication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimesecurechat.client.peerCommunication.PeerSocketManager;
import com.realtimesecurechat.client.utils.Crypto;
import com.realtimesecurechat.client.UI.ConnectionRequestDialog;

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

        // Use the ConnectionRequestDialog class
        ConnectionRequestDialog dialog = new ConnectionRequestDialog(userId);
        ConnectionRequestDialog.UserResponse userResponse = dialog.showDialog();

        switch (userResponse) {
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
