package com.realtimesecurechat.client.UI;

import javax.swing.*;

public class InitialDialogBox {
    private String userId;

    public String getUserId() {
        return userId;
    }

    public boolean showDialog() {
        userId = JOptionPane.showInputDialog(
                null,
                "Enter your User ID:",
                "User Registration",
                JOptionPane.PLAIN_MESSAGE
        );
        return userId != null && !userId.isEmpty();
    }
}