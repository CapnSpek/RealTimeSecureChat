package server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import server.utils.BiDirectionalMap;

import java.security.Security;

import javax.crypto.Cipher;

@ServerEndpoint(value = "/chat")
public class WebSocketServer {

    static {
        // Register Bouncy Castle Provider
        Security.addProvider(new BouncyCastleProvider());
    }

    // JSON object mapper
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Bidirectional map for client user ID to session ID
    private static final BiDirectionalMap<String, String> clientUserIdToSessionIdBiDiMap = new BiDirectionalMap<>();
    // Map for client user ID to public key, and session ID to session object
    private static final Map<String, PublicKey> clientUserIdToPublicKey = new ConcurrentHashMap<>();
    private static final Map<String, Session> clientSessionIdToSession = new ConcurrentHashMap<>();
    // Map for connection requests from one client to another
    private static final Map<String, Set<String>> connectionRequests = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        clientSessionIdToSession.put(session.getId(), session);
        System.out.println("New connection: " + session.getId());
    }

    @OnMessage
    public void onMessage(String message, Session session) throws IOException {
        JsonNode jsonMessage = objectMapper.readTree(message);

        // Verify the signature
        if (!verifyClientSignature(jsonMessage, session)) {
            sendErrorMessage(session, "Invalid signature.");
            return;
        }

        if (!jsonMessage.has("messageType")) {
            sendErrorMessage(session, "Invalid message format: 'messageType' missing.");
            return;
        }

        String requestType = jsonMessage.get("messageType").asText();
        switch (requestType) {
            case "Register":
                handleRegisterClient(jsonMessage, session);
                break;
            case "Connection request":
                handleConnectionRequest(jsonMessage, session);
                break;
            case "Connection approval":
                handleConnectionApproval(jsonMessage, session);
                break;
            default:
                sendErrorMessage(session, "Invalid message format: Value of 'messageType' is not recognized.");
                break;
        }
    }

    /*
     * Handle client registration request
     * Expected JSON format:
     * {
     *  "messageType": "register",
     *  "userId": "client1",
     *  "publicKey": "base64EncodedPublicKey"
     *  "signature": "base64EncodedSignature"
     * }
     */
    private void handleRegisterClient(JsonNode jsonMessage, Session session) throws IOException {
        String userId = jsonMessage.get("userId").asText();
        String publicKeyString = jsonMessage.get("publicKey").asText();

        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PublicKey publicKey = keyFactory.generatePublic(spec);

            clientUserIdToSessionIdBiDiMap.put(userId, session.getId());
            clientUserIdToPublicKey.put(userId, publicKey);

            System.out.println("Registered client: " + userId);
            sendConfirmationMessage(session, "Registered user.", userId);
        } catch (Exception e) {
            sendErrorMessage(session, "Error processing public key for registration.");
        }
    }

    /*
     * Handle connection request from one client to another
     * Expected JSON format:
     * {
     *  "messageType": "connectionRequest",
     *  "targetUserId": "client2"
     *  "signature": "base64EncodedSignature"
     * }
     */
    private void handleConnectionRequest(JsonNode jsonMessage, Session session) throws IOException {
        String requesterUserId = null;
        String targetUserId = null;

        try {
            // Retrieve the requester user ID based on the session
            requesterUserId = clientUserIdToSessionIdBiDiMap.getKey(session.getId());
            if (requesterUserId == null) {
                System.out.println("Unregistered user attempted to send a connection request. Session ID: " + session.getId());
                return;
            }

            // Extract the target user ID from the JSON message
            targetUserId = jsonMessage.get("targetUserId").asText();

            // Check if the target user exists
            String targetSessionId = clientUserIdToSessionIdBiDiMap.getValue(targetUserId);
            if (targetSessionId == null) {
                System.out.println("Connection request failed: Target user not found. Requester: " + requesterUserId + ", Target: " + targetUserId);
                return;
            }

            // Retrieve the target session
            Session targetSession = clientSessionIdToSession.get(targetSessionId);
            if (targetSession == null) {
                System.out.println("Connection request failed: Target user session not found. Requester: " + requesterUserId + ", Target: " + targetUserId);
                return;
            }

            // Retrieve the requester's public key
            PublicKey requesterPublicKey = clientUserIdToPublicKey.get(requesterUserId);
            if (requesterPublicKey == null) {
                System.out.println("Connection request failed: Requester public key not found. Requester: " + requesterUserId + ", Target: " + targetUserId);
                return;
            }

            // Forward the connection request to the target user
            String publicKeyString = Base64.getEncoder().encodeToString(requesterPublicKey.getEncoded());
            sendConnectionRequest(targetSession, requesterUserId, publicKeyString);
            System.out.println("Forwarded connection request from " + requesterUserId + " to " + targetUserId);

            // Add the requester to the set of users requesting connection to the target user
            connectionRequests.computeIfAbsent(targetUserId, k -> ConcurrentHashMap.newKeySet()).add(requesterUserId);

        } catch (Exception e) {
            System.out.println("Exception while handling connection request. Requester: " + requesterUserId + ", Target: " + targetUserId);
            e.printStackTrace();
        } finally {
            // Always send a confirmation message to the requester
            sendConfirmationMessage(session, "The request has been sent if the client is online.", targetUserId != null ? targetUserId : "unknown");
        }
    }

    /*
     * Handle connection approval from target client to requester
     * Expected JSON format:
     * {
     *  "messageType": "connectionApproval",
     *  "requesterUserId": "client2",
     *  "connectionDetails": "Encrypted connection details"
     *  "signature": "base64EncodedSignature"
     * }
     */
    private void handleConnectionApproval(JsonNode jsonMessage, Session session) throws IOException {
        String approvingUserId = null;
        String requesterUserId = null;
        String connectionDetails = null;
        boolean success = false; // Flag to indicate if the operation was successful

        try {
            // Retrieve approving user ID from the session
            approvingUserId = clientUserIdToSessionIdBiDiMap.getKey(session.getId());
            if (approvingUserId == null) {
                System.out.println("Connection approval failed: Approving user not found for session ID: " + session.getId());
                return;
            }

            // Extract requester user ID and connection details from the JSON message
            requesterUserId = jsonMessage.get("requesterUserId").asText();
            connectionDetails = jsonMessage.get("connectionDetails").asText();

            // Check if the requester exists in the set of users who requested connection to the approving user
            Set<String> requesterSet = connectionRequests.get(approvingUserId);
            if (requesterSet == null || !requesterSet.contains(requesterUserId)) {
                System.out.println("Connection approval failed: No matching request found. Approver: " + approvingUserId + ", Requester: " + requesterUserId);
                return;
            }

            // Retrieve the requester session
            String requesterSessionId = clientUserIdToSessionIdBiDiMap.getValue(requesterUserId);
            if (requesterSessionId == null) {
                System.out.println("Connection approval failed: Requester session not found. Approver: " + approvingUserId + ", Requester: " + requesterUserId);
                return;
            }

            Session requesterSession = clientSessionIdToSession.get(requesterSessionId);
            if (requesterSession == null) {
                System.out.println("Connection approval failed: Requester session object not found. Approver: " + approvingUserId + ", Requester: " + requesterUserId);
                return;
            }

            // Send approval message to the requester
            sendApprovalMessage(requesterSession, approvingUserId, connectionDetails);
            System.out.println("Forwarded connection approval from " + approvingUserId + " to " + requesterUserId);

            success = true; // Mark operation as successful
        } catch (Exception e) {
            System.out.println("Exception while handling connection approval. Approver: " + approvingUserId + ", Requester: " + requesterUserId);
            e.printStackTrace();
        } finally {
            // Update maps and sets only if the operation was successful
            if (success) {
                Set<String> requesterSet = connectionRequests.get(approvingUserId);
                if (requesterSet != null) {
                    requesterSet.remove(requesterUserId);
                    if (requesterSet.isEmpty()) {
                        connectionRequests.remove(approvingUserId); // Remove entry if no more requesters
                    }
                }
            }
        }
    }

    /*
     * Send an error message to the client
     * Expected JSON format:
     * {
     * "messageType": "Error",
     * "message": "Error message"
     * }
     */
    private void sendErrorMessage(Session session, String errorMessage) throws IOException {
        String errorJson = objectMapper.writeValueAsString(
                Map.of("messageType", "Error", "message", errorMessage)
        );
        sendEncryptedMessage(session, errorJson);
    }

    /*
     * Send a confirmation message to the client
     * Expected JSON format:
     * {
     * "messageType": "Confirmation",
     * "message": "Confirmation message",
     * "user": "client1"
     * }
    */
    private void sendConfirmationMessage(Session session, String message, String userId) throws IOException {
        String confirmationJson = objectMapper.writeValueAsString(
                Map.of("messageType", "Confirmation", "message", message, "user", userId)
        );
        sendEncryptedMessage(session, confirmationJson);
    }

    /*
     * Send connection request to the target client
     * Expected JSON format:
     * {
     * "messageType": "connectionRequest",
     * "fromUserId": "client1",
     * "requesterPublicKey": "base64EncodedPublicKey"
     * }
     */
    private void sendConnectionRequest(Session targetSession, String requesterUserId, String publicKeyString) throws IOException {
        String requestJson = objectMapper.writeValueAsString(
                Map.of("messageType", "connectionRequest",
                        "fromUserId", requesterUserId,
                        "requesterPublicKey", publicKeyString)
        );
        sendEncryptedMessage(targetSession, requestJson);
    }

    /*
     * Send connection approval to the requester client
     * Expected JSON format:
     * {
     * "messageType": "Approval",
     * "user": "client",
     * "connectionDetails": "Encrypted connection details"
     * }
     */
    private void sendApprovalMessage(Session requesterSession, String approvingUserId, String connectionDetails) throws IOException {
        String approvalJson = objectMapper.writeValueAsString(
                Map.of("messageType", "Approval",
                        "user", approvingUserId,
                        "connectionDetails", connectionDetails)
        );
        sendEncryptedMessage(requesterSession, approvalJson);
    }

    private void sendEncryptedMessage(Session session, String plainText) {
        try {
            // Encrypt the message with the client's public key
            String sessionId = session.getId();
            String userId = clientUserIdToSessionIdBiDiMap.getKey(sessionId);
            PublicKey publicKey = clientUserIdToPublicKey.get(userId);
            if (publicKey == null) {
                System.out.println("Public key not found for user: " + userId);
                return;
            }
            Cipher cipher = Cipher.getInstance("ECIES", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedMessage = cipher.doFinal(plainText.getBytes());
            String encodedMessage = Base64.getEncoder().encodeToString(encryptedMessage);

            session.getBasicRemote().sendText(encodedMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Verify the client signature
     * @param jsonMessage The incoming JSON message
     * @param session The session of the client
     * @return true if the signature is valid, false otherwise
     */
    private boolean verifyClientSignature(JsonNode jsonMessage, Session session) {
        try {
            // Retrieve the signature
            if (!jsonMessage.has("signature")) {
                System.out.println("Signature missing in the message.");
                return false;
            }

            String signatureBase64 = jsonMessage.get("signature").asText();
            String sessionId = session.getId();

            // Handle the "register" case where the public key is part of the message
            if (jsonMessage.has("messageType") && "Register".equalsIgnoreCase(jsonMessage.get("messageType").asText())) {
                if (!jsonMessage.has("publicKey") || !jsonMessage.has("userId")) {
                    System.out.println("Public key or userId missing in the registration message.");
                    return false;
                }

                // Extract the public key from the message
                String publicKeyString = jsonMessage.get("publicKey").asText();
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                PublicKey publicKey = keyFactory.generatePublic(spec);

                // Prepare the payload for verification (exclude the signature field)
                ObjectNode messageCopy = (ObjectNode) jsonMessage.deepCopy();
                messageCopy.remove("signature");
                String payload = objectMapper.writeValueAsString(messageCopy);

                // Verify the signature
                byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
                Signature signature = Signature.getInstance("SHA256withECDSA");
                signature.initVerify(publicKey);
                signature.update(payload.getBytes());

                boolean isValid = signature.verify(signatureBytes);
                if (!isValid) {
                    System.out.println("Signature verification failed for registration.");
                }
                return isValid;
            }

            // For all other cases, retrieve the userId and public key from the map
            String userId = clientUserIdToSessionIdBiDiMap.getKey(sessionId);
            PublicKey publicKey = clientUserIdToPublicKey.get(userId);
            if (publicKey == null) {
                System.out.println("Public key not found for user: " + userId);
                return false;
            }

            // Prepare the payload for verification (exclude the signature field)
            ObjectNode messageCopy = (ObjectNode) jsonMessage.deepCopy();
            messageCopy.remove("signature");
            String payload = objectMapper.writeValueAsString(messageCopy);

            // Verify the signature
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            Signature signature = Signature.getInstance("SHA256withECDSA");
            signature.initVerify(publicKey);
            signature.update(payload.getBytes());

            boolean isValid = signature.verify(signatureBytes);
            if (!isValid) {
                System.out.println("Signature verification failed for user: " + userId);
            }
            return isValid;

        } catch (Exception e) {
            System.out.println("Exception during signature verification: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @OnClose
    public void onClose(Session session) {
        String userId = clientUserIdToSessionIdBiDiMap.getKey(session.getId());
        clientUserIdToSessionIdBiDiMap.removeByKey(session.getId());
        clientUserIdToPublicKey.remove(userId);
        connectionRequests.remove(userId);
        connectionRequests.values().forEach(set -> set.remove(userId));
        clientSessionIdToSession.remove(session.getId());
        System.out.println("Connection closed: " + session.getId() + " (User: " + userId + ")");
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        String userId = clientUserIdToSessionIdBiDiMap.getKey(session.getId());
        clientUserIdToSessionIdBiDiMap.removeByKey(session.getId());
        clientSessionIdToSession.remove(session.getId());
        System.out.println("Error: " + throwable.getMessage() + " (User: " + userId + ")");
    }
}