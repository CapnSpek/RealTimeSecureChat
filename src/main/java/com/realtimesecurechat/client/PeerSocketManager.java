package com.realtimesecurechat.client;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;

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
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
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
            System.out.println("Sent message to peer: Hi from Peer");

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
            System.out.println("Sent to peer: Hello back!");
            writer.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}