package com.realtimesecurechat.client.UI;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

public class ChatPanel extends JPanel {
    private final JTextArea chatArea;
    private final JTextField messageInput;
    private final JButton sendButton;
    private String recipient; // Store the recipient

    public void setRecipient(String recipient) {
        this.recipient = recipient; // Set the recipient
    }

    public String getRecipient() {
        return recipient; // Get the recipient
    }

    public ChatPanel(BiConsumer<String, String> onSendMessage) {
        setLayout(new BorderLayout());

        // Chat Display Area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        // Message Input Area
        JPanel inputPanel = new JPanel(new BorderLayout());
        messageInput = new JTextField();
        sendButton = new JButton("Send");

        sendButton.addActionListener(e -> {
            String message = messageInput.getText();
            if (!message.isEmpty()) {
                onSendMessage.accept(message, recipient); // Use the stored recipient
                messageInput.setText("");
            }
        });

        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    public void appendMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n");
    }

    public void clearChat() {
        chatArea.setText("");
    }
}