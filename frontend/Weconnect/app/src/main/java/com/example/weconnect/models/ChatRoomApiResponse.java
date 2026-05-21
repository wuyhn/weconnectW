package com.example.weconnect.models;

import java.util.List;

/**
 * Typed model for chat room API response.
 * Replaces Map<String, Object> parsing.
 */
public class ChatRoomApiResponse {
    private long id;
    private Long postId;
    private String title;
    private String type;
    private long ownerId;
    private String ownerName;
    private boolean active;
    private String inactiveStatusLabel;
    private String subtitle;
    private String postStatusLabel;
    private String lastMessagePreview;
    private String lastMessageTime;
    private Boolean isFriend;
    private Boolean isMessageRequest;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserAvatarUrl;
    private Boolean isBlockedByMe;
    private Boolean hasBlockedMe;
    private Boolean isBlockedBetweenUsers;
    private Boolean otherUserOnline;
    private Long otherUserLastActiveMins;
    private int unreadCount;
    private String activityDateDisplay;
    private int memberCount;
    private int maxMembers;
    private List<MemberInfo> members;
    private String createdAt;

    // Getters
    public long getId() { return id; }
    public Long getPostId() { return postId; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public long getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public boolean isActive() { return active; }
    public String getInactiveStatusLabel() { return inactiveStatusLabel; }
    public String getSubtitle() { return subtitle; }
    public String getPostStatusLabel() { return postStatusLabel; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public String getLastMessageTime() { return lastMessageTime; }
    public boolean isFriend() { return Boolean.TRUE.equals(isFriend); }
    public boolean isMessageRequest() { return Boolean.TRUE.equals(isMessageRequest); }
    public long getOtherUserId() { return otherUserId != null ? otherUserId : -1; }
    public String getOtherUserName() { return otherUserName; }
    public String getOtherUserAvatarUrl() { return otherUserAvatarUrl; }
    public boolean isBlockedByMe() { return Boolean.TRUE.equals(isBlockedByMe); }
    public boolean hasBlockedMe() { return Boolean.TRUE.equals(hasBlockedMe); }
    public boolean isBlockedBetweenUsers() { return Boolean.TRUE.equals(isBlockedBetweenUsers); }
    public boolean isOtherUserOnline() { return Boolean.TRUE.equals(otherUserOnline); }
    public Long getOtherUserLastActiveMins() { return otherUserLastActiveMins; }
    public int getUnreadCount() { return unreadCount; }
    public String getActivityDateDisplay() { return activityDateDisplay; }
    public int getMemberCount() { return memberCount; }
    public int getMaxMembers() { return maxMembers; }
    public List<MemberInfo> getMembers() { return members; }
    public String getCreatedAt() { return createdAt; }

    public static class MemberInfo {
        private long id;
        private String fullName;
        private String role;
        private String avatarUrl;
        private Boolean isBlockedByMe;
        private Boolean hasBlockedMe;
        private Boolean isBlockedBetweenUsers;

        public long getId() { return id; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
        public String getAvatarUrl() { return avatarUrl; }
        public boolean isBlockedByMe() { return Boolean.TRUE.equals(isBlockedByMe); }
        public boolean hasBlockedMe() { return Boolean.TRUE.equals(hasBlockedMe); }
        public boolean isBlockedBetweenUsers() { return Boolean.TRUE.equals(isBlockedBetweenUsers); }
    }
}
