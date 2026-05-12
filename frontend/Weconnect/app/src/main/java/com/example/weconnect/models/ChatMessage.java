package com.example.weconnect.models;

import java.io.Serializable;

public class ChatMessage implements Serializable {

    private final String id;
    private final long senderId;
    private final String senderName;
    private final String content;
    private final String timeLabel;
    private final boolean sentByCurrentUser;

    public ChatMessage(String id, long senderId, String senderName, String content, String timeLabel, boolean sentByCurrentUser) {
        this.id = id;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.timeLabel = timeLabel;
        this.sentByCurrentUser = sentByCurrentUser;
    }

    public ChatMessage(String id, String senderName, String content, String timeLabel, boolean sentByCurrentUser) {
        this(id, 0L, senderName, content, timeLabel, sentByCurrentUser);
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
}
