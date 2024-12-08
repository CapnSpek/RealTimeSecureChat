package com.realtimesecurechat.client.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.URL;

public class Network {
    public static String getPrivateIP() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                // Skip loopback and down interfaces
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Filter for site-local IPv4 addresses only
                    if (addr.isSiteLocalAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback to loopback if no address is found
        return "127.0.0.1";
    }

    public static String getPublicIP() {
        String publicIP = "Unable to retrieve public IP";
        try {
            URL url = new URL("https://api.ipify.org"); // Use ipify API
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000); // Timeout for connection
            connection.setReadTimeout(5000);    // Timeout for reading data

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                publicIP = in.readLine(); // Public IP is the first line of the response
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return publicIP;
    }
}