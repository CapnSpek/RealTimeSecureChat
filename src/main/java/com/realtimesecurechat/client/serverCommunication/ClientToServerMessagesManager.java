package com.realtimesecurechat.client.serverCommunication;

import com.realtimesecurechat.client.serverCommunication.models.Message;
import com.realtimesecurechat.client.peerCommunication.PeerSocketManager;
import com.realtimesecurechat.client.utils.Crypto;

import java.security.KeyPair;
import java.security.PublicKey;

public class ClientToServerMessagesManager {

    private final PeerSocketManager peerSocketManager;
    private final WebSocketClientToServer webSocketClientToServer;
    private final KeyPair keyPair;

    public ClientToServerMessagesManager(PeerSocketManager peerSocketManager, WebSocketClientToServer webSocketClientToServer, KeyPair keyPair) {
        this.peerSocketManager = peerSocketManager;
        this.webSocketClientToServer = webSocketClientToServer;
        this.keyPair = keyPair;
    }

    public void performRegistration(String clientUserId) {
        Message registrationMessage = new Message("Register")
                .addField("userId", clientUserId)
                .addField("publicKey", Crypto.encodeKey(keyPair.getPublic()));

        webSocketClientToServer.sendMessage(registrationMessage);
    }

    /* Send a connection request to another user
       JSON format:
       {
           "messageType": "Connection request",
           "toUserId": "username"
       }
     */
    public void sendConnectionRequest(String userId) {
        // Create the connection request message using the Message class
        Message connectionRequestMessage = new Message("Connection request")
                .addField("toUserId", userId);

        // Send the connection request message
        webSocketClientToServer.sendMessage(connectionRequestMessage);
        peerSocketManager.addOutgoingRequest(userId);
        System.out.println("Sent connection request to: " + userId);
    }

    /* Approve a connection request
         JSON format:
         {
              "messageType": "Connection approval",
              "requesterUserId": "username",
              "connectionDetails": "encryptedConnectionDetails"
         }
     */
    public void approveConnection(String requesterUserId) {
        // Retrieve the requester's public key
        PublicKey requesterPublicKey = peerSocketManager.getIncomingRequestPublicKey(requesterUserId);
        if (requesterPublicKey == null) {
            System.out.println("No pending request for: " + requesterUserId);
            return;
        }

        // Encrypt connection details using the Crypto utility class
        String connectionDetails = peerSocketManager.getConnectionDetails();
        String encryptedDetails = Crypto.encryptMessage(connectionDetails, requesterPublicKey);

        // Create the approval message using the Message class
        Message approvalMessage = new Message("Connection approval")
                .addField("requesterUserId", requesterUserId)
                .addField("connectionDetails", encryptedDetails);

        // Send the approval message
        webSocketClientToServer.sendMessage(approvalMessage);
        System.out.println("Approved connection for: " + requesterUserId);
    }

    // Reject a connection request
    public void rejectConnection(String requesterUserId) {
        // Remove the requester's public key from the map
        if(!peerSocketManager.hasIncomingRequest(requesterUserId)) {
            System.out.println("No pending request from: " + requesterUserId);
            return;
        }
        peerSocketManager.removeIncomingRequest(requesterUserId);

        // Create the rejection message using the Message class
        Message rejectionMessage = new Message("Connection rejection")
                .addField("requesterUserId", requesterUserId);

        // Send rejection message
        webSocketClientToServer.sendMessage(rejectionMessage);
        System.out.println("Rejected connection for: " + requesterUserId);
    }
}