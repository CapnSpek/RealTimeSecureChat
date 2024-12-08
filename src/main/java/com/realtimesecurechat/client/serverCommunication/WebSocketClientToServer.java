package com.realtimesecurechat.client.serverCommunication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimesecurechat.client.models.Message;
import com.realtimesecurechat.client.utils.Crypto;
import jakarta.websocket.*;
import java.util.Map;

import java.net.URI;
import java.security.KeyPair;

@ClientEndpoint
public class WebSocketClientToServer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KeyPair keyPair;
    private final ServerMessageHandlerRegistry serverMessageHandlerRegistry;
    private Session session;

    public WebSocketClientToServer(URI endpointURI, ServerMessageHandlerRegistry serverMessageHandlerRegistry, KeyPair keyPair) {
        try {
            this.serverMessageHandlerRegistry = serverMessageHandlerRegistry;
            this.keyPair = keyPair;
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);

        } catch (Exception e) {
            throw new RuntimeException("Error initializing WebSocketClient", e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to server: " + session.getId());
    }

    @OnMessage
    public void onMessage(String encryptedMessage) {
        try {
            // Delegate decryption and handling to the message handler registry
            JsonNode jsonMessage = serverMessageHandlerRegistry.decryptAndParseMessage(encryptedMessage);

            String messageType = jsonMessage.get("messageType").asText();
            System.out.println("Received message of type: " + messageType);
            serverMessageHandlerRegistry.handleMessage(messageType, jsonMessage);
        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendMessage(Message message) {
        try {
            // Convert the message to a Map and sign the payload
            Map<String, Object> messageMap = message.toMap();
            String payload = objectMapper.writeValueAsString(messageMap);
            String signature = Crypto.signMessage(payload, keyPair.getPrivate());

            // Add the signature to the message map
            messageMap.put("signature", signature);

            // Convert the updated message map back to JSON and send it
            String signedMessage = objectMapper.writeValueAsString(messageMap);
            session.getAsyncRemote().sendText(signedMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}