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
    private String type;

    // Getters
    public long getId() { return id; }
    public long getRoomId() { return roomId; }
    public long getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public boolean isSentByCurrentUser() { return sentByCurrentUser; }
    public String getCreatedAt() { return createdAt; }
    public String getType() { return type; }
    public boolean isSystemMessage() { return "SYSTEM".equals(type); }
    public boolean isSummaryMessage() { return "SUMMARY".equals(type); }
}
