package com.realtimesecurechat.client.peerCommunication;

public interface MessageListener {
    void onMessageReceived(String username, String message);
}
