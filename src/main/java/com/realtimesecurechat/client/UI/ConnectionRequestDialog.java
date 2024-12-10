package com.realtimesecurechat.client.UI;

import javax.swing.*;

public class ConnectionRequestDialog {
    public enum UserResponse {
        ACCEPT,
        REJECT,
        NO_RESPONSE
    }

    private final String userId;

    public ConnectionRequestDialog(String userId) {
        this.userId = userId;
    }

    public UserResponse showDialog() {
        int response = JOptionPane.showConfirmDialog(
                null,
                "Connection request from: " + userId + "\nDo you want to accept?",
                "Connection Request",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            return UserResponse.ACCEPT;
        } else if (response == JOptionPane.NO_OPTION) {
            return UserResponse.REJECT;
        } else {
            return UserResponse.NO_RESPONSE;
        }
    }
}