package com.realtimesecurechat.client.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

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
}