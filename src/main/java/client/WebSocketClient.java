package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
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
    private PublicKey requesterPublicKey; // For storing the requester’s public key
    private String clientUserId;

    public WebSocketClient(URI endpointURI) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(256);
            KeyPair keyPair = keyGen.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();

            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Connected to server: " + session.getId());

        // Send initial registration message with client ID and public key
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter your client user ID: ");
        clientUserId = scanner.nextLine();

        Map<String, String> message = new HashMap<>();
        message.put("messageType", "Register");
        message.put("userId", clientUserId);
        message.put("publicKey", Base64.getEncoder().encodeToString(publicKey.getEncoded()));

        sendSignedMessage(message);
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

                default:
                    System.err.println("Unhandled messageType: " + messageType);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to process message from server.");
            e.printStackTrace();
        }
    }

    /**
     * Decrypts the received message using the client's private key.
     *
     * @param encryptedMessage The Base64-encoded encrypted message
     * @return The decrypted message as a plain string
     * @throws Exception If decryption fails
     */
    private String decryptMessage(String encryptedMessage) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        Cipher cipher = Cipher.getInstance("ECIES", "BC"); // Use Bouncy Castle for ECIES decryption
        cipher.init(Cipher.DECRYPT_MODE, privateKey); // Decrypt using the client's private key
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    private void handleConnectionRequest(JsonNode jsonMessage) {
        try {
            // Retrieve the requester’s public key
            String requesterUserId = jsonMessage.get("fromUserId").asText();
            String requesterPublicKeyString = jsonMessage.get("requesterPublicKey").asText();
            byte[] publicKeyBytes = Base64.getDecoder().decode(requesterPublicKeyString);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            requesterPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            System.out.println("Connection request from: " + requesterUserId);
            System.out.println("Stored public key of requester for encrypted response.");

            // Approve connection with encrypted details
            approveConnection(requesterUserId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void approveConnection(String requesterUserId) {
        if (requesterPublicKey == null) {
            System.err.println("Requester public key not available. Cannot s[el encrypted details.");
            return;
        }
        try {
            // Encrypt connection details with requester’s public key
            String connectionDetails = "Sample connection details";
            Cipher cipher = Cipher.getInstance("ECIES", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, requesterPublicKey);
            byte[] encryptedDetails = cipher.doFinal(connectionDetails.getBytes());

            // Create the approval message
            Map<String, String> approvalMessage = new HashMap<>();
            approvalMessage.put("messageType", "Connection approval");
            approvalMessage.put("requesterUserId", requesterUserId);
            approvalMessage.put("connectionDetails", Base64.getEncoder().encodeToString(encryptedDetails));

            sendSignedMessage(approvalMessage);
            System.out.println("Sent encrypted connection details to " + requesterUserId);

        } catch (Exception e) {
            System.err.println("Failed to encrypt connection details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendSignedMessage(Map<String, String> messageData) {
        try {
            // Serialize the message to JSON
            String payload = objectMapper.writeValueAsString(messageData);

            // Sign the message
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes());
            String signatureBase64 = Base64.getEncoder().encodeToString(signature.sign());

            // Add the signature to the message
            messageData.put("signature", signatureBase64);

            // Send the message
            String signedJsonMessage = objectMapper.writeValueAsString(messageData);
            session.getAsyncRemote().sendText(signedJsonMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}