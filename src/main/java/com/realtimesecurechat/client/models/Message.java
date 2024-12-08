package com.realtimesecurechat.client.models;

import java.util.HashMap;
import java.util.Map;

public class Message {
    private final String messageType;
    private final Map<String, String> fields;

    // Constructor
    public Message(String messageType) {
        this.messageType = messageType;
        this.fields = new HashMap<>();
    }

    // Add a field to the message
    public Message addField(String key, String value) {
        fields.put(key, value);
        return this;
    }

    // Get a field value
    public String getField(String key) {
        return fields.get(key);
    }

    // Get the message type
    public String getMessageType() {
        return messageType;
    }

    // Convert to a Map for JSON serialization
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("messageType", messageType);
        map.putAll(fields);
        return map;
    }
}