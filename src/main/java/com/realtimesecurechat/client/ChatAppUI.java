package com.realtimesecurechat.client;

import javax.swing.*;
import java.awt.*;

public class ChatAppUI {
    private final WebSocketClient webSocketClient;
    private JFrame frame;
    private JList<String> chatList;
    private DefaultListModel<String> chatListModel;
    private JTextArea chatArea;

    public ChatAppUI(WebSocketClient client) {
        this.webSocketClient = client;
    }

    public void initialize() {
        frame = new JFrame("Real-Time Secure Chat");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem connectItem = new JMenuItem("Connect to User");
        connectItem.addActionListener(e -> openConnectToUserDialog());
        fileMenu.add(connectItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        // Chat List Panel (Left)
        chatListModel = new DefaultListModel<>();
        chatList = new JList<>(chatListModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.addListSelectionListener(e -> updateChatView(chatList.getSelectedValue()));

        JScrollPane chatListScrollPane = new JScrollPane(chatList);
        chatListScrollPane.setPreferredSize(new Dimension(200, 0));
        frame.add(chatListScrollPane, BorderLayout.WEST);

        // Chat View Panel (Right)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatAreaScrollPane = new JScrollPane(chatArea);
        frame.add(chatAreaScrollPane, BorderLayout.CENTER);

        // Input Field and Send Button
        JPanel inputPanel = new JPanel(new BorderLayout());
        JTextField messageInput = new JTextField();
        JButton sendButton = new JButton("Send");

        sendButton.addActionListener(e -> {
            String message = messageInput.getText();
            if (!message.isEmpty() && chatList.getSelectedValue() != null) {
                webSocketClient.sendMessageToPeer(chatList.getSelectedValue(), message); // Backend call
                chatArea.append("Me: " + message + "\n");
                messageInput.setText("");
            }
        });

        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        frame.add(inputPanel, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    private void openConnectToUserDialog() {
        String targetUserId = JOptionPane.showInputDialog(frame, "Enter the User ID to connect:", "Connect to User", JOptionPane.PLAIN_MESSAGE);
        if (targetUserId != null && !targetUserId.isEmpty()) {
            webSocketClient.requestConnection(targetUserId); // Backend call
            chatListModel.addElement(targetUserId); // Add to chat list
        }
    }

    private void updateChatView(String userId) {
        // Clear the chat area and load messages for the selected user
        chatArea.setText("");
        // Optionally, fetch past messages from the backend or a local cache
    }
}