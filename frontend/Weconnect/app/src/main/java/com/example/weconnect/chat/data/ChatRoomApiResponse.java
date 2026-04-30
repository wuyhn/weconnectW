package com.example.weconnect.chat.data;

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
    public List<MemberInfo> getMembers() { return members; }
    public String getCreatedAt() { return createdAt; }

    public static class MemberInfo {
        private long id;
        private String fullName;
        private String role;
        private String avatarUrl;

        public long getId() { return id; }
        public String getFullName() { return fullName; }
        public String getRole() { return role; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
