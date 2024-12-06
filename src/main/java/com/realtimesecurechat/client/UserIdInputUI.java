package com.realtimesecurechat.client;

import javax.swing.*;
import java.awt.*;

public class UserIdInputUI {
    private final WebSocketClient webSocketClient;

    public UserIdInputUI(WebSocketClient client) {
        this.webSocketClient = client;
        initialize();
    }

    private void initialize() {
        JFrame frame = new JFrame("Enter User ID");
        frame.setSize(300, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTextField userIdField = new JTextField();
        JButton submitButton = new JButton("Submit");

        frame.add(new JLabel("Enter User ID:", SwingConstants.CENTER), BorderLayout.NORTH);
        frame.add(userIdField, BorderLayout.CENTER);
        frame.add(submitButton, BorderLayout.SOUTH);

        submitButton.addActionListener(e -> {
            String userId = userIdField.getText();
            if (userId.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "User ID cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            // Register with the server
            webSocketClient.performRegistration(userId);
            frame.dispose();
            new ChatAppUI(webSocketClient).initialize(); // Open the main chat UI
        });

        frame.setVisible(true);
    }
}