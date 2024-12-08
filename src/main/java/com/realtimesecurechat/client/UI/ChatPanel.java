package com.realtimesecurechat.client.UI;

import javax.swing.*;
import java.awt.*;
import java.util.function.BiConsumer;

public class ChatPanel extends JPanel {
    private final JTextArea chatArea;
    private final JTextField messageInput;
    private final JButton sendButton;

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
                onSendMessage.accept(message, chatArea.getName());
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