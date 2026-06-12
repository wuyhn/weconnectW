package com.example.weconnect.models;

import java.io.Serializable;

public class ChatMessage implements Serializable {

    private final String id;
    private final long senderId;
    private final String senderName;
    private final String content;
    private final String timeLabel;
    private final boolean sentByCurrentUser;
    private final boolean systemMessage;
    private final String type;
    private final String senderAvatarUrl;

    public ChatMessage(String id, long senderId, String senderName, String content, String timeLabel, boolean sentByCurrentUser) {
        this(id, senderId, senderName, content, timeLabel, sentByCurrentUser, false, null);
    }

    public ChatMessage(String id, long senderId, String senderName, String content, String timeLabel, boolean sentByCurrentUser, boolean systemMessage) {
        this(id, senderId, senderName, content, timeLabel, sentByCurrentUser, systemMessage, null);
    }

    public ChatMessage(String id, long senderId, String senderName, String content, String timeLabel, boolean sentByCurrentUser, boolean systemMessage, String type) {
        this(id, senderId, senderName, content, timeLabel, sentByCurrentUser, systemMessage, type, null);
    }

    public ChatMessage(String id, long senderId, String senderName, String content, String timeLabel, boolean sentByCurrentUser, boolean systemMessage, String type, String senderAvatarUrl) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timeLabel = timeLabel;
        this.sentByCurrentUser = sentByCurrentUser;
        this.systemMessage = systemMessage;
        this.type = type;
        this.senderAvatarUrl = senderAvatarUrl;
    }

    public ChatMessage(String id, String senderName, String content, String timeLabel, boolean sentByCurrentUser) {
        this(id, 0L, senderName, content, timeLabel, sentByCurrentUser, false, null);
    }

    public String getId() {
        return id;
    }

    public long getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public String getTimeLabel() {
        return timeLabel;
    }

    public boolean isSentByCurrentUser() {
        return sentByCurrentUser;
    }

    public String getType() {
        return type;
    }

    public String getSenderAvatarUrl() {
        return senderAvatarUrl;
    }

    public boolean isSystemMessage() {
        return systemMessage || "SYSTEM".equals(type);
    }

    public boolean isSummaryMessage() {
        return "SUMMARY".equals(type);
    }
}
