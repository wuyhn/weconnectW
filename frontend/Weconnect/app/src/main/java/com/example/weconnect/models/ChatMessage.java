package com.example.weconnect.models;

import java.io.Serializable;

public class ChatMessage implements Serializable {

    private final String id;
    private final String senderName;
    private final String content;
    private final String timeLabel;
    private final boolean sentByCurrentUser;

    public ChatMessage(String id, String senderName, String content, String timeLabel, boolean sentByCurrentUser) {
        this.id = id;
        this.senderName = senderName;
        this.content = content;
        this.timeLabel = timeLabel;
        this.sentByCurrentUser = sentByCurrentUser;
    }

    public String getId() {
        return id;
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
