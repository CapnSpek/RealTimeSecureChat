package com.realtimesecurechat.client.UI;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.Optional;

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
        // Create an Alert dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Connection Request");
        alert.setHeaderText("Connection Request from: " + userId);
        alert.setContentText("Do you want to accept the connection request?");

        // Customize buttons
        ButtonType acceptButton = new ButtonType("Accept");
        ButtonType rejectButton = new ButtonType("Reject");
        ButtonType cancelButton = new ButtonType("Cancel");

        // Set buttons on the dialog
        alert.getButtonTypes().setAll(acceptButton, rejectButton, cancelButton);

        // Show the dialog and wait for user input
        Optional<ButtonType> result = alert.showAndWait();

        // Determine the user response
        if (result.isPresent()) {
            if (result.get() == acceptButton) {
                return UserResponse.ACCEPT;
            } else if (result.get() == rejectButton) {
                return UserResponse.REJECT;
            }
        }
        return UserResponse.NO_RESPONSE; // Default if no response or cancel
    }
}