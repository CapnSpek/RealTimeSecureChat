package com.realtimesecurechat.client.UI;

import javax.swing.*;

public class ConnectToPeerDialog {
    public String getPeerUserId() {
        return JOptionPane.showInputDialog(
                null,
                "Enter Peer User ID:",
                "Connect to Peer",
                JOptionPane.PLAIN_MESSAGE
        );
    }
}