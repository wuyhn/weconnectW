package com.example.weconnect.models;

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
        REPORT_CONFIRMED,
        REPORT_PENALTY,
        POST_EXPIRED,
        ACTIVITY_CANCELLED,
        GENERAL
    }

    private long id;
    private long userId;
    private NotificationType type;
    private String message;
    private String relatedUsername;
    private Long relatedPostId;
    private Long relatedUserId;
    private Long relatedReportId;

    @SerializedName("read")
    private boolean isRead;

    @SerializedName("actioned")
    private boolean isActioned;

    private String actionResult;

    private String createdAt;
    private String senderAvatarUrl;
    private String postTitle;

    // Getters
    public long getId() { return id; }
    public long getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public String getRelatedUsername() { return relatedUsername; }
    public Long getRelatedPostId() { return relatedPostId; }
    public Long getRelatedUserId() { return relatedUserId; }
    public Long getRelatedReportId() { return relatedReportId; }
    public boolean isRead() { return isRead; }
    public boolean isActioned() { return isActioned; }
    public String getActionResult() { return actionResult; }
    public String getCreatedAt() { return createdAt; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }
    public String getPostTitle() { return postTitle; }

    // Setters
    public void setId(long id) { this.id = id; }
    public void setRead(boolean read) { isRead = read; }
    public void setActioned(boolean actioned) { isActioned = actioned; }
    public void setActionResult(String actionResult) { this.actionResult = actionResult; }
    public void setPostTitle(String postTitle) { this.postTitle = postTitle; }
}
