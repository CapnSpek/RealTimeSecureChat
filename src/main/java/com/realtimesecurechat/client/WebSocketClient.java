package com.realtimesecurechat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.net.InetAddress;
import java.net.URI;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@ClientEndpoint
public class WebSocketClient {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final PeerSocketManager peerSocketManager;
    private Session session;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final Map<String, PublicKey> requesterPublicKeys = new HashMap<>();
    private String clientUserId;
    String connectionDetails;

    public WebSocketClient(URI endpointURI, int peerPort) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();

            // Initialize PeerSocketManager for peer-to-peer communication
            this.peerSocketManager = new PeerSocketManager(peerPort);
            this.peerSocketManager.startServer();

            container.connectToServer(this, endpointURI);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to server: " + session.getId());
    }

    public void performRegistration(String clientUserId) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your client user ID: ");
        //clientUserId = scanner.nextLine();

        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress(); // Fetch the machine's IP address
            this.connectionDetails = hostAddress + ":" + peerSocketManager.getPort();
            System.out.println("Connection details: " + connectionDetails);
        } catch (Exception e) {
            e.printStackTrace();
        }


        Map<String, String> message = new HashMap<>();
        message.put("messageType", "Register");
        message.put("userId", clientUserId);
        message.put("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));

        sendSignedMessage(message);
    }

    @OnMessage
    public void onMessage(String encryptedMessage) {
        System.out.println("Received encrypted message: " + encryptedMessage);
        try {
            // Decrypt the message using the private key
            String decryptedMessage = decryptMessage(encryptedMessage);
            System.out.println("Decrypted message: " + decryptedMessage);

            // Parse the decrypted message as JSON
            JsonNode jsonMessage = objectMapper.readTree(decryptedMessage);

            if (!jsonMessage.has("messageType")) {
                System.err.println("Invalid server response: Missing 'messageType'");
                return;
            }

            String messageType = jsonMessage.get("messageType").asText();
            switch (messageType) {
                case "connectionRequest":
                    handleConnectionRequest(jsonMessage);
                    break;

                case "Error":
                    System.err.println("Error from server: " + jsonMessage.get("message").asText());
                    break;

                case "Confirmation":
                    System.out.println("Server confirmation: " + jsonMessage.get("message").asText());
                    break;

                case "Approval":
                    handleApproval(jsonMessage);
                    break;

                default:
                    System.err.println("Unhandled messageType: " + messageType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to process message from server.");
            e.printStackTrace();
        }
    }

    private void rejectConnection(String requesterUserId) {
        // Remove the requester's public key from the map
        PublicKey removedKey = requesterPublicKeys.remove(requesterUserId);
        if (removedKey == null) {
            System.err.println("No pending request from: " + requesterUserId);
            return;
        }

        // Send rejection message to the server
        Map<String, String> rejectionMessage = new HashMap<>();
        rejectionMessage.put("messageType", "Connection rejection");
        rejectionMessage.put("requesterUserId", requesterUserId);

        sendSignedMessage(rejectionMessage);
        System.out.println("Rejected connection for: " + requesterUserId);
    }

    private void handleApproval(JsonNode jsonMessage) {
        try {
            String approverUserId = jsonMessage.get("user").asText();
            String encryptedConnectionDetails = jsonMessage.get("connectionDetails").asText();

            String connectionDetails = decryptMessage(encryptedConnectionDetails);
            System.out.println("Connection approved by " + approverUserId + ": " + connectionDetails);

            String[] parts = connectionDetails.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            peerSocketManager.connectToPeer(host, port);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleConnectionRequest(JsonNode jsonMessage) {
        try {
            String requesterUserId = jsonMessage.get("fromUserId").asText();
            String requesterPublicKeyString = jsonMessage.get("requesterPublicKey").asText();
            byte[] publicKeyBytes = Base64.getDecoder().decode(requesterPublicKeyString);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey requesterPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            requesterPublicKeys.put(requesterUserId, requesterPublicKey);
            approveConnection(requesterUserId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestConnection(String targetUserId) {
        Map<String, String> message = new HashMap<>();
        message.put("messageType", "Connection request");
        message.put("targetUserId", targetUserId);
        sendSignedMessage(message);
    }

    private void approveConnection(String requesterUserId) {
        try {
            PublicKey requesterPublicKey = requesterPublicKeys.get(requesterUserId);
            if (requesterPublicKey == null) return;

            Cipher cipher = Cipher.getInstance("ECIES", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, requesterPublicKey);
            byte[] encryptedDetails = cipher.doFinal(connectionDetails.getBytes());

            Map<String, String> message = new HashMap<>();
            message.put("messageType", "Connection approval");
            message.put("requesterUserId", requesterUserId);
            message.put("connectionDetails", Base64.getEncoder().encodeToString(encryptedDetails));

            sendSignedMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessageToPeer(String userId, String message) {
        // Example peer communication, assuming you already have peer connections established
        System.out.println("Sending message to peer: " + userId + " - " + message);
        //peerSocketManager.sendMessageToPeer(userId, message);
    }

    private String decryptMessage(String encryptedMessage) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(encryptedBytes));
    }

    private void sendSignedMessage(Map<String, String> messageData) {
        try {
            String payload = objectMapper.writeValueAsString(messageData);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes());
            messageData.put("signature", Base64.getEncoder().encodeToString(signature.sign()));

            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(messageData));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}