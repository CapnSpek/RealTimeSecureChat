/*package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.Socket;

public class GoServerCommunication {
    private Socket goServerSocket; // TCP Socket for communicating with the Go Server
    private BufferedReader goServerReader; // Reader for Go Server responses
    private BufferedWriter goServerWriter; // Writer for sending commands to Go Server
    private static final ObjectMapper objectMapper = new ObjectMapper(); // JSON object mapper

    public GoServerCommunication(String goServerHost, int goServerPort) {
        try {
            // Initialize the socket, reader, and writer
            // Initialize connection to the local Go Server
            this.goServerSocket = new Socket(goServerHost, goServerPort);
            this.goServerReader = new BufferedReader(new InputStreamReader(goServerSocket.getInputStream()));
            this.goServerWriter = new BufferedWriter(new OutputStreamWriter(goServerSocket.getOutputStream()));
        } catch (Exception e) {
            e.printStackTrace();
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
            String connectionDetails = message.get("payload").asText();
            System.out.println("Received connection info: " + connectionDetails);

            // Forward the connection details to the central server (or next peer)
            forwardConnectionInfo(connectionDetails);
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
}
*/