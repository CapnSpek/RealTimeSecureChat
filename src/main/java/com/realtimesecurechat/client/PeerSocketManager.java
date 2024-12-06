package com.realtimesecurechat.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class PeerSocketManager {

    private final int port;
    private ServerSocket serverSocket;

    public PeerSocketManager(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void startServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Peer server started on port " + port);

            new Thread(() -> {
                while (true) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> handleClient(clientSocket)).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectToPeer(String host, int port) {
        try (Socket socket = new Socket(host, port)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            writer.write("Hi from Peer\n");
            writer.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();
            System.out.println("Received: " + response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String message = reader.readLine();
            System.out.println("Received from peer: " + message);

            writer.write("Hello back!\n");
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}