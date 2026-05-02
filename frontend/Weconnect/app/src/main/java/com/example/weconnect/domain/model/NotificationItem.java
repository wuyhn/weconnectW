package com.example.weconnect.domain.model;


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

    private String id;
    private String userId;
    private String type;
    private String message;
    private String relatedUsername;
    private String relatedPostId;
    private String relatedUserId;

    private boolean isRead;

    private boolean isActioned;

    private String createdAt;
    private String senderAvatarUrl;
    private String actorName;

    // Getters
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getRelatedUsername() { return relatedUsername; }
    public String getRelatedPostId() { return relatedPostId; }
    public String getRelatedUserId() { return relatedUserId; }
    public boolean isRead() { return isRead; }
    public boolean isActioned() { return isActioned; }
    public String getCreatedAt() { return createdAt; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public String getActorName() { return actorName; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setType(String type) { this.type = type; }
    public void setMessage(String message) { this.message = message; }
    public void setRelatedUsername(String relatedUsername) { this.relatedUsername = relatedUsername; }
    public void setRelatedPostId(String relatedPostId) { this.relatedPostId = relatedPostId; }
    public void setRelatedUserId(String relatedUserId) { this.relatedUserId = relatedUserId; }
    public void setRead(boolean read) { isRead = read; }
    public void setActioned(boolean actioned) { isActioned = actioned; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public void setSenderAvatarUrl(String senderAvatarUrl) { this.senderAvatarUrl = senderAvatarUrl; }
    public void setActorName(String actorName) { this.actorName = actorName; }
}
