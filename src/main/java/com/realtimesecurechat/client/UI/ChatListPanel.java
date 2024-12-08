package com.realtimesecurechat.client.UI;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class ChatListPanel extends JPanel {
    private final DefaultListModel<String> chatListModel;
    private final JList<String> chatList;

    public ChatListPanel(Consumer<String> onChatSelected) {
        setLayout(new BorderLayout());
        chatListModel = new DefaultListModel<>();
        chatList = new JList<>(chatListModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && chatList.getSelectedValue() != null) {
                onChatSelected.accept(chatList.getSelectedValue());
            }
        });

        JScrollPane scrollPane = new JScrollPane(chatList);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void addChat(String username) {
        if (!chatListModel.contains(username)) {
            chatListModel.addElement(username);
        }
    }

    public String getSelectedChat() {
        return chatList.getSelectedValue();
    }
}