package com.example.weconnect.models;

/**
 * Typed model for chat message API response.
 * Replaces Map<String, Object> parsing.
 */
public class ChatMessageApiResponse {
    private long id;
    private long roomId;
    private long senderId;
    private String senderName;
    private String content;
    private boolean sentByCurrentUser;
    private String createdAt;

    // Getters
    public long getId() { return id; }
    public long getRoomId() { return roomId; }
    public long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public boolean isSentByCurrentUser() { return sentByCurrentUser; }
    public String getCreatedAt() { return createdAt; }
}
