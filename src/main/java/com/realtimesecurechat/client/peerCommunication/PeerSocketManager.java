package com.realtimesecurechat.client.peerCommunication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimesecurechat.client.utils.Network;
import com.realtimesecurechat.client.utils.Crypto;
import com.realtimesecurechat.client.models.Message;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

public class PeerSocketManager {

    private final int port;
    private ServerSocket serverSocket;
    private final String connectionDetails;
    private final KeyPair keyPair;
    private String userId;

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Stores users to which connection requests have been sent (username)
    private final Set<String> outgoingConnectionRequests = new HashSet<>();

    // Stores pending connection requests (username -> PublicKey)
    private final ConcurrentHashMap<String, PublicKey> incomingConnectionRequests = new ConcurrentHashMap<>();

    // Stores active connections (username -> PeerConnection)
    private final ConcurrentHashMap<String, PeerConnection> activeConnections = new ConcurrentHashMap<>();

    private PeerConnectionListener connectionListener;

    public PeerSocketManager(int port, KeyPair keyPair) {
        this.port = port;
        this.keyPair = keyPair;
        String privateIP = Network.getPrivateIP();
        connectionDetails = privateIP + ":" + port;
    }

    public int getPort() {
        return port;
    }

    public String getConnectionDetails() { return connectionDetails; }

    public void setConnectionListener(PeerConnectionListener listener) { this.connectionListener = listener; }

    public void addIncomingConnectionRequest(String username, PublicKey publicKey) { incomingConnectionRequests.put(username, publicKey); }
    public boolean hasIncomingRequest(String username) { return incomingConnectionRequests.containsKey(username); }
    public void removeIncomingRequest(String username) { incomingConnectionRequests.remove(username); }

    public PublicKey getIncomingRequestPublicKey(String username) { return incomingConnectionRequests.get(username); }
    public void addOutgoingRequest(String username) { outgoingConnectionRequests.add(username); }
    public void removeOutgoingRequest(String username) { outgoingConnectionRequests.remove(username); }
    public boolean hasOutgoingRequest(String username) { return outgoingConnectionRequests.contains(username); }

    // Start the server
    public void startServer() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("Peer server started on port " + port);

            // Continuously accept incoming connections
            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleIncomingConnection(clientSocket);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectToPeer(String userId, String connectionDetails, String publicKeyString) {
        try {
            // Parse connection details
            String[] parts = connectionDetails.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            System.out.println("Connecting to peer at: " + host + ":" + port);

            // Establish socket connection
            Socket socket = new Socket(host, port);
            System.out.println("Connected to peer at: " + host + ":" + port);

            // Decode the user's public key
            PublicKey publicKey = Crypto.decodeKey(publicKeyString);
            System.out.println("Public key decoded for user: " + userId);

            // Create a peer connection and add it to active connections
            PeerConnection connection = new PeerConnection(userId, publicKey, socket);
            activeConnections.put(userId, connection);
            outgoingConnectionRequests.remove(userId);
            System.out.println("Added connection to active connections: " + userId);
            System.out.println("Removed from outgoing requests: " + userId);

            // Notify listener about the connection
            connectionListener.onConnectionEstablished(userId);
            System.out.println("Notified listener about connection with: " + userId);

            // Create the first message using the Message class
            Message initialMessage = new Message("initialMessage")
                    .addField("userId", this.userId);
            String signature = Crypto.signMessage(initialMessage.toString(), keyPair.getPrivate());
            initialMessage.addField("signature", signature);
            System.out.println("Sending initial message with userId, signature to user: " + userId);

            // Serialize the message to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonMessage = objectMapper.writeValueAsString(initialMessage.toMap());

            // Encrypt the JSON message with the user's public key
            String encryptedMessage = Crypto.encryptMessage(jsonMessage, publicKey);
            System.out.println("Encrypted message to user: " + userId);

            // Send the encrypted message through the socket
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println(encryptedMessage);
            System.out.println("Sent encrypted message to user: " + userId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingConnection(Socket clientSocket) {
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))
        ) {
            System.out.println("Incoming connection from: " + clientSocket.getInetAddress());
            // Read the first encrypted message from the peer
            String encryptedMessage = reader.readLine();

            // Decrypt the message
            String decryptedMessage = Crypto.decryptMessage(encryptedMessage, keyPair.getPrivate());

            // Parse the decrypted message into JSON
            JsonNode messageNode = objectMapper.readTree(decryptedMessage);

            // Extract userId and signature
            String userId = messageNode.get("userId").asText();
            String signature = messageNode.get("signature").asText();
            System.out.println("Received message from user: " + userId);

            // Retrieve the user's public key
            PublicKey publicKey = incomingConnectionRequests.get(userId);
            if (publicKey == null) {
                System.out.println("Public key for user " + userId + " not found. Rejecting connection.");
                clientSocket.close();
                return;
            }
            System.out.println("Public key found for user: " + userId);

            System.out.println(decryptedMessage);
            // Verify the signature
            boolean isSignatureValid = Crypto.verifyMessage(decryptedMessage, signature, publicKey);
            if (!isSignatureValid) {
                System.err.println("Invalid signature for user " + userId + ". Rejecting connection.");
                clientSocket.close();
                return;
            }
            System.out.println("Signature verified for user: " + userId);

            // Add the connection to activeConnections
            activeConnections.put(userId, new PeerConnection(userId, publicKey, clientSocket));
            System.out.println("Connection established with user: " + userId);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    // Send a message to an active connection
    public void sendMessage(String username, String message) {
        PeerConnection connection = activeConnections.get(username);
        if (connection != null) {
            connection.sendMessage(message);
        } else {
            System.out.println("No active connection with: " + username);
        }
    }

    public List<String> getChatHistory(String username) {
        PeerConnection connection = activeConnections.get(username);
        if (connection != null) {
            return connection.getConversationHistory();
        }
        return Collections.emptyList();
    }
}