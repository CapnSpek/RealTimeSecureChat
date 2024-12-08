package com.realtimesecurechat.client.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Cipher;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static com.realtimesecurechat.server.WebSocketServer.objectMapper;

public class Crypto {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    public static PublicKey decodeKey(String keyStr) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(keyStr);
            return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode key", e);
        }
    }

    public static String encodeKey(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String signMessage(String message, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes());
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public static String decryptMessage(String encryptedMessage, PrivateKey privateKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedMessage);
        System.out.println("Base 64 Decoded encrypted message: " + encryptedMessage);
        Cipher cipher = Cipher.getInstance("ECIES", "BC");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);
    }

    // Encrypt a message using the provided PublicKey
    public static String encryptMessage(String message, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance("ECIES", "BC");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(message.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt message", e);
        }
    }

    public static boolean verifyMessage(String jsonMessage, PublicKey publicKey) {
        try {
            // Parse the JSON message
            JsonNode messageNode = objectMapper.readTree(jsonMessage);
            System.out.println("Entered verifyMessage");

            // Extract the signature and the message
            String signatureBase64 = messageNode.get("signature").asText();

            // Remove the "signature" field from the message
            ObjectNode messageWithoutSignature = (ObjectNode) messageNode.deepCopy();
            messageWithoutSignature.remove("signature");
            System.out.println("Found message without signature");

            // Serialize the remaining message back to JSON
            String messageToVerify = objectMapper.writeValueAsString(messageWithoutSignature);

            // Decode the Base64 signature
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);

            // Initialize the Signature object for verification
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(messageToVerify.getBytes());

            System.out.println("message verification: " + verifier.verify(signatureBytes));

            // Verify the signature
            return verifier.verify(signatureBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}