package com.realtimesecurechat.client.UI;

import com.realtimesecurechat.client.peerCommunication.PeerSocketManager;
import com.realtimesecurechat.client.serverCommunication.ClientToServerMessagesManager;
import com.realtimesecurechat.client.serverCommunication.WebSocketClientToServer;
import com.realtimesecurechat.client.models.Message;

import javax.swing.*;
import java.awt.*;

public class ChatAppUI {
    private final JFrame frame;
    private final ChatListPanel chatListPanel;
    private final ChatPanel chatPanel;

    private final WebSocketClientToServer webSocketClientToServer;
    private final PeerSocketManager peerSocketManager;
    private final ClientToServerMessagesManager messagesManager;

    public ChatAppUI(WebSocketClientToServer serverClient, PeerSocketManager peerManager, ClientToServerMessagesManager messagesManager) {
        this.webSocketClientToServer = serverClient;
        this.peerSocketManager = peerManager;
        this.messagesManager = messagesManager;

        frame = new JFrame("Real-Time Secure Chat");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Initialize Panels
        chatListPanel = new ChatListPanel(this::onChatSelected);
        chatPanel = new ChatPanel(this::sendMessage);

        // Set PeerSocketManager Listener
        peerSocketManager.setConnectionListener(this::onConnectionEstablished);

        // Add Panels to Frame
        frame.add(chatListPanel, BorderLayout.WEST);
        frame.add(chatPanel, BorderLayout.CENTER);

        // Add Menu
        setupMenu();

        frame.setVisible(true);
    }

    private void setupMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem requestConnectionToPeerItem = new JMenuItem("Request Connection to Peer");

        requestConnectionToPeerItem.addActionListener(e -> {
            ConnectToPeerDialog dialog = new ConnectToPeerDialog();
            String peerUserId = dialog.getPeerUserId();
            if (peerUserId != null && !peerUserId.isEmpty()) {
                // Use the ClientToServerMessagesManager to handle the connection request
                messagesManager.sendConnectionRequest(peerUserId);

                // Add the peer to the chat list panel
                chatListPanel.addChat(peerUserId);
            }
        });

        fileMenu.add(requestConnectionToPeerItem);
        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);
    }

    private void onChatSelected(String username) {
        chatPanel.clearChat();
        peerSocketManager.getChatHistory(username).forEach(chat -> {
            chatPanel.appendMessage("Peer", chat);
        });
    }

    public void onConnectionEstablished(String username) {
        SwingUtilities.invokeLater(() -> chatListPanel.addChat(username));
    }

    private void sendMessage(String message, String recipient) {
        if (recipient != null) {
            Message messageObj = new Message("chatMessage");
            messageObj.addField("message", message);
            messageObj.addField("toUserId", recipient);
            peerSocketManager.sendMessage(messageObj);
            chatPanel.appendMessage("Me", message);
        }
    }
}