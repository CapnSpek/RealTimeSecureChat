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
import java.util.*;
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

    private MessageListener messageListener;

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public PeerSocketManager(int port, KeyPair keyPair) {
        this.port = port;
        this.keyPair = keyPair;
        String privateIP = Network.getPrivateIP();
        connectionDetails = privateIP + ":" + port;
        System.out.println("Connection details initialized at: " + connectionDetails);
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

    public KeyPair getKeyPair() { return keyPair; }
    public PublicKey getConnectionPublicKey(String username) { return activeConnections.get(username).getPublicKey(); }

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

    // Notify the listener that a connection has been established
    private void notifyConnectionEstablished(String username) {
        if (connectionListener != null) {
            connectionListener.onConnectionEstablished(username);
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
            PeerConnection connection = new PeerConnection(userId, publicKey, socket, this);
            connection.setMessageListener((username, message) -> {
                if (messageListener != null) {
                    messageListener.onMessageReceived(username, message);
                }
            });
            activeConnections.put(userId, connection);
            outgoingConnectionRequests.remove(userId);
            System.out.println("Added connection to active connections: " + userId);
            System.out.println("Removed from outgoing requests: " + userId);

            // Notify listener about the connection
            notifyConnectionEstablished(userId);
            System.out.println("Notified listener about connection with: " + userId);

            // Send the initial message
            Message initialMessage = new Message("initialMessage")
                    .addField("userId", this.userId)
                    .addField("toUserId", userId);
            sendMessage(initialMessage);

            System.out.println("Sent encrypted message to user: " + userId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleIncomingConnection(Socket clientSocket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            System.out.println("Incoming connection from: " + clientSocket.getInetAddress());
            // Read the first encrypted message from the peer
            String encryptedMessage = reader.readLine();
            System.out.println("Received encrypted message: " + encryptedMessage);

            // Decrypt the message
            String decryptedMessage = Crypto.decryptMessage(encryptedMessage, keyPair.getPrivate());
            System.out.println("Decrypted message: " + decryptedMessage);

            // Parse the decrypted message into JSON
            JsonNode messageNode = objectMapper.readTree(decryptedMessage);

            // Extract userId
            String userId = messageNode.get("userId").asText();
            System.out.println("Received message from user: " + userId);

            // Retrieve the user's public key
            PublicKey publicKey = incomingConnectionRequests.get(userId);
            if (publicKey == null) {
                System.out.println("Public key for user " + userId + " not found. Rejecting connection.");
                clientSocket.close(); // Close the socket if not authorized
                return;
            }
            System.out.println("Public key found for user: " + userId);

            // Verify the signature
            boolean isSignatureValid = Crypto.verifyMessage(decryptedMessage, publicKey);
            if (!isSignatureValid) {
                System.err.println("Invalid signature for user " + userId + ". Rejecting connection.");
                clientSocket.close(); // Close the socket if signature is invalid
                return;
            }
            System.out.println("Signature verified for user: " + userId);

            // Add the connection to activeConnections
            PeerConnection connection = new PeerConnection(userId, publicKey, clientSocket, this);
            activeConnections.put(userId, connection);
            incomingConnectionRequests.remove(userId);
            connection.setMessageListener((username, message) -> {
                if (messageListener != null) {
                    messageListener.onMessageReceived(username, message);
                }
            });
            System.out.println("Connection established with user: " + userId);

            // Notify listener about the connection
            notifyConnectionEstablished(userId);
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
    public void sendMessage(Message message) {
        try {
            // Convert the message to a Map and sign the payload
            Map<String, Object> messageMap = message.toMap();
            System.out.println("Message map: " + messageMap);

            String toUserId = messageMap.get("toUserId").toString();
            messageMap.remove("toUserId");
            System.out.println("Removed toUserId from message map: " + messageMap);

            String payload = objectMapper.writeValueAsString(messageMap);
            String signature = Crypto.signMessage(payload, keyPair.getPrivate());

            // Add the signature to the message map
            messageMap.put("signature", signature);
            System.out.println("Signature added to message map: " + messageMap);

            // Convert the updated message map back to JSON
            String signedMessage = objectMapper.writeValueAsString(messageMap);
            System.out.println("Signed message: " + signedMessage);

            // Encrypt the message
            PublicKey publicKey = activeConnections.get(toUserId).getPublicKey();
            String encryptedMessage = Crypto.encryptMessage(signedMessage, publicKey);
            System.out.println("Encrypted message: " + encryptedMessage);

            // Get the connection for the user
            PeerConnection connection = activeConnections.get(toUserId);
            System.out.println("Connection: " + connection);

            if (connection != null) {
                connection.sendMessage(encryptedMessage, messageMap.get("message").toString());
            } else {
                System.out.println("No active connection with: " + toUserId);
            }
        } catch (Exception e) {
            e.printStackTrace();
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