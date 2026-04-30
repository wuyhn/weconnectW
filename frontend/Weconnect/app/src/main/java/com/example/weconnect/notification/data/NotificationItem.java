package com.example.weconnect.notification.data;

import com.google.gson.annotations.SerializedName;

public class NotificationItem {

    public enum NotificationType {
        FRIEND_REQUEST_RECEIVED,
        FRIEND_REQUEST_SENT,
        FRIEND_ACCEPTED,
        JOIN_REQUEST,
        JOIN_APPROVED,
        JOIN_REJECTED,
        ADMIN_WARNING,
        ADMIN_ACTION,
        POST_EXPIRED,
        GENERAL
    }

    private long id;
    private long userId;
    private NotificationType type;
    private String message;
    private String relatedUsername;
    private Long relatedPostId;
    private Long relatedUserId;

    @SerializedName("read")
    private boolean isRead;

    @SerializedName("actioned")
    private boolean isActioned;

    private String createdAt;
    private String senderAvatarUrl;

    // Getters
    public long getId() { return id; }
    public long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public String getRelatedUsername() { return relatedUsername; }
    public Long getRelatedPostId() { return relatedPostId; }
    public Long getRelatedUserId() { return relatedUserId; }
    public boolean isRead() { return isRead; }
    public boolean isActioned() { return isActioned; }
    public String getCreatedAt() { return createdAt; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setRead(boolean read) { isRead = read; }
    public void setActioned(boolean actioned) { isActioned = actioned; }
}
