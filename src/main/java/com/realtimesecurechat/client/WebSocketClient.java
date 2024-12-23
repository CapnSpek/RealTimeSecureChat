package com.realtimesecurechat.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.*;
import java.net.Socket;
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
        // Register Bouncy Castle Provider
        Security.addProvider(new BouncyCastleProvider());
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Session session;
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final Map<String, PublicKey> requesterPublicKeys = new HashMap<>(); // Map of requester IDs to public keys
    private final BufferedReader goServerReader; // Reader for Go Server responses
    private final BufferedWriter goServerWriter; // Writer for sending commands to Go Server
    private String clientUserId;
    private String connectionDetails;

    public WebSocketClient(URI endpointURI, String goServerHost, int goServerPort) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);

            // Initialize connection to the local Go Server
            // TCP Socket for communicating with the Go Server
            Socket goServerSocket = new Socket(goServerHost, goServerPort);
            this.goServerReader = new BufferedReader(new InputStreamReader(goServerSocket.getInputStream()));
            this.goServerWriter = new BufferedWriter(new OutputStreamWriter(goServerSocket.getOutputStream()));

            // Start listening for Go Server messages
            startGoServerListener();
            sendGetConnectionInformationToGoServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void startGoServerListener() {
        new Thread(() -> {
            try {
                while (true) {
                    // Listen for messages from the Go Server
                    String response = goServerReader.readLine();
                    if (response != null) {
                        handleGoServerMessage(response);
                    }

                }
            } catch (IOException e) {
                System.err.println("Error reading from Go Server: " + e.getMessage());
            }
        }).start();
    }

    private void handleGoServerMessage(String message) {
        try {
            JsonNode jsonMessage = objectMapper.readTree(message);

            String messageType = jsonMessage.get("type").asText(); // Message type from the Go Server
            switch (messageType) {
                case "connectionInfo":
                    processConnectionInfo(jsonMessage);
                    break;

                case "ack":
                    processAcknowledgment(jsonMessage);
                    break;

                case "error":
                    processErrorMessage(jsonMessage);
                    break;

                default:
                    System.err.println("Unhandled message type from Go Server: " + messageType);
            }
        } catch (Exception e) {
            System.err.println("Failed to process message from Go Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processConnectionInfo(JsonNode message) {
        try {
            this.connectionDetails = message.get("payload").asText();
            System.out.println("Received connection info: " + connectionDetails);
        } catch (Exception e) {
            System.err.println("Failed to process connection info: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processAcknowledgment(JsonNode message) {
        System.out.println("Acknowledgment received from Go Server: " + message.get("payload").asText());
    }

    private void processErrorMessage(JsonNode message) {
        System.err.println("Error from Go Server: " + message.get("payload").asText());
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to server: " + session.getId());

        // Perform registration
        performRegistration();
    }

    private void performRegistration() {
        // Prompt user for client ID
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your client user ID: ");
        clientUserId = scanner.nextLine();

        // Prepare registration message
        Map<String, String> message = new HashMap<>();
        message.put("messageType", "Register");
        message.put("userId", clientUserId);
        message.put("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));

        // Send the registration message
        sendSignedMessage(message);
    }

    public void fetchConnectionInfo() {
        try {
            // Send a `getInfo` request to the Go Server
            Map<String, String> command = new HashMap<>();
            command.put("type", "getInfo");

            String commandJson = objectMapper.writeValueAsString(command);
            goServerWriter.write(commandJson);
            goServerWriter.newLine();
            goServerWriter.flush();
        } catch (IOException e) {
            System.err.println("Failed to communicate with Go Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /*
    * Forward connection details to the Go Server for processing
    * @param connectionDetails The connection details to forward
    * @param user The user who sent the connection details
    * JSON Structure:
    * {
    *   "messageType": "connect",
    *   "connectionDetails": "SDP/ICE information",
    *   "user": "User ID"
    * }
     */

    private void forwardConnectionInfoToGoServer(String connectionDetails, String user) {
        // Forward connection info to the central WebSocket server
        try {
            Map<String, String> message = new HashMap<>();
            message.put("messageType", "connect");
            message.put("connectionDetails", connectionDetails);
            message.put("user", user);

            // Convert the command to JSON
            String messageJson = objectMapper.writeValueAsString(message);

            // Send the JSON to the Go Server via the TCP socket
            goServerWriter.write(messageJson);
            goServerWriter.newLine();
            goServerWriter.flush();

            System.out.println("Forwarded connection info to the Go Server.");
        } catch (IOException e) {
            System.err.println("Failed to send connection details to the Go Server.");
            e.printStackTrace();
        }
    }

    public void requestConnection(String targetUserId) {
        Map<String, String> message = new HashMap<>();
        message.put("messageType", "Connection request");
        message.put("targetUserId", targetUserId);

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

                default:
                    System.err.println("Unhandled messageType: " + messageType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to process message from server.");
            e.printStackTrace();
        }
    }


    private void handleApproval(JsonNode jsonMessage) {
        try {
            String approverUserId = jsonMessage.get("user").asText();
            String encryptedConnectionDetails = jsonMessage.get("connectionDetails").asText();

            // Decrypt the connection details using the private key
            String decryptedConnectionDetails = decryptMessage(encryptedConnectionDetails);

            System.out.println("Received connection approval from: " + approverUserId);
            System.out.println("Decrypted connection details: " + decryptedConnectionDetails);

            // Additional processing of connection details (e.g., parse SDP/ICE information)
            processConnectionDetails(decryptedConnectionDetails, approverUserId);
        } catch (Exception e) {
            System.err.println("Failed to handle approval message.");
            e.printStackTrace();
        }
    }

    /*
     * Forward connection details to the Go Server for processing
     * @param connectionDetails The connection details to forward
     * @param user The user who sent the connection details
     * JSON Structure:
     * {
     *   "messageType": "connect",
     *   "connectionDetails": "SDP/ICE information",
     *   "user": "User ID"
     * }
     */
    private void processConnectionDetails(String connectionDetails, String approverUserId) {
        // Add logic to handle or parse the decrypted connection details (SDP/ICE)
        System.out.println("Processing connection details: " + connectionDetails);
        forwardConnectionInfoToGoServer(connectionDetails, approverUserId);
    }

    private void handleReceivedConnectionInfo(JsonNode jsonMessage) {
        String connectionDetails = jsonMessage.get("connectionDetails").asText();
        System.out.println("Received connection details: " + connectionDetails);

        // Forward these details to the Go Server for processing
        try {
            Map<String, String> command = new HashMap<>();
            command.put("type", "processConnectionDetails");
            command.put("payload", connectionDetails);

            String commandJson = objectMapper.writeValueAsString(command);
            goServerWriter.write(commandJson);
            goServerWriter.newLine();
            goServerWriter.flush();
        } catch (IOException e) {
            System.err.println("Failed to send connection details to Go Server.");
            e.printStackTrace();
        }
    }

    private String decryptMessage(String encryptedMessage) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    private void handleConnectionRequest(JsonNode jsonMessage) {
        try {
            String requesterUserId = jsonMessage.get("fromUserId").asText();
            String requesterPublicKeyString = jsonMessage.get("requesterPublicKey").asText();
            byte[] publicKeyBytes = Base64.getDecoder().decode(requesterPublicKeyString);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey requesterPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            System.out.println("Connection request from: " + requesterUserId);

            // Store the requester's public key in the map
            requesterPublicKeys.put(requesterUserId, requesterPublicKey);

            // Automatically approve for now (this can be replaced with a UI hook)
            approveConnection(requesterUserId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void approveConnection(String requesterUserId) {
        PublicKey requesterPublicKey = requesterPublicKeys.remove(requesterUserId); // Remove from map
        if (requesterPublicKey == null) {
            System.err.println("No pending request from: " + requesterUserId);
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("ECIES", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, requesterPublicKey);
            byte[] encryptedDetails = cipher.doFinal(connectionDetails.getBytes());

            Map<String, String> approvalMessage = new HashMap<>();
            approvalMessage.put("messageType", "Connection approval");
            approvalMessage.put("requesterUserId", requesterUserId);
            approvalMessage.put("connectionDetails", Base64.getEncoder().encodeToString(encryptedDetails));

            sendSignedMessage(approvalMessage);
            System.out.println("Approved connection for: " + requesterUserId);
        } catch (Exception e) {
            System.err.println("Failed to encrypt connection details for: " + requesterUserId);
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

    private void sendSignedMessage(Map<String, String> messageData) {
        try {
            String payload = objectMapper.writeValueAsString(messageData);

            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes());
            String signatureBase64 = Base64.getEncoder().encodeToString(signature.sign());

            messageData.put("signature", signatureBase64);

            String signedJsonMessage = objectMapper.writeValueAsString(messageData);
            session.getAsyncRemote().sendText(signedJsonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendGetConnectionInformationToGoServer() {
        try {
            // Send a `getInfo` request to the Go Server
            Map<String, String> command = new HashMap<>();
            command.put("type", "getInfo");

            String commandJson = objectMapper.writeValueAsString(command);
            goServerWriter.write(commandJson);
            goServerWriter.newLine();
            goServerWriter.flush();
        } catch (IOException e) {
            System.err.println("Failed to communicate with Go Server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}