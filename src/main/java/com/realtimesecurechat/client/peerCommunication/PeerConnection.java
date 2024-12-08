package com.realtimesecurechat.client.peerCommunication;

import java.io.*;
import java.net.Socket;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class PeerConnection {
    private final String username;
    private final PublicKey publicKey;
    private final Socket socket;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final List<String> conversationHistory;
    private volatile boolean listening; // Flag to control the listening thread

    public PeerConnection(String username, PublicKey publicKey, Socket socket) throws IOException {
        this.username = username;
        this.publicKey = publicKey;
        this.socket = socket;
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.conversationHistory = new ArrayList<>();
        this.listening = true; // Start listening
        startListening();
    }

    public PeerConnection(String username, PublicKey publicKey) throws IOException {
        this(username, publicKey, new Socket());
    }

    public String getUsername() {
        return username;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public List<String> getConversationHistory() {
        return conversationHistory;
    }

    public void sendMessage(String message) {
        try {
            writer.write(message + "\n");
            writer.flush();
            conversationHistory.add("Me: " + message);
        } catch (IOException e) {
            System.err.println("Failed to send message: " + e.getMessage());
        }
    }

    private void startListening() {
        new Thread(() -> {
            try {
                String message;
                while (listening && (message = reader.readLine()) != null) {
                    System.out.println("Received from " + username + ": " + message);
                    conversationHistory.add(username + ": " + message);
                }
            } catch (IOException e) {
                if (listening) { // Ignore exceptions during shutdown
                    System.err.println("Error in listening thread for " + username + ": " + e.getMessage());
                }
            } finally {
                close(); // Ensure the socket is closed on thread exit
            }
        }).start();
    }

    public void close() {
        listening = false; // Stop the listening loop
        try {
            if (!socket.isClosed()) {
                socket.close();
                System.out.println("Connection with " + username + " closed.");
            }
        } catch (IOException e) {
            System.err.println("Failed to close connection for " + username + ": " + e.getMessage());
        }
    }
}