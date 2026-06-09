package com.example.weconnect.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ChatRoom implements Serializable {

    public static final String TYPE_GROUP = "group";
    public static final String TYPE_DIRECT = "direct";
    public static final String TYPE_FRIEND_GROUP = "friend_group";
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_MESSAGE_REQUESTS = "message_requests";

    private final String id;
    private final String title;
    private final String subtitle;
    private final String postStatusLabel;
    private final String type;
    private final int avatarResId;
    private final boolean active;
    private final String inactiveStatusLabel;
    private final List<ChatMessage> messages;
    private String ownerUsername;
    private final List<String> members;
    private final List<String> pendingMembers;
    private long lastActivityTime;

    public ChatRoom(String id, String title, String type, int avatarResId, List<ChatMessage> messages) {
        this(id, title, null, null, type, avatarResId, true, "", messages, null, new ArrayList<>(), new ArrayList<>());
    }

    public ChatRoom(String id, String title, String type, int avatarResId, boolean active,
                    String inactiveStatusLabel, List<ChatMessage> messages) {
        this(id, title, null, null, type, avatarResId, active, inactiveStatusLabel, messages, null, new ArrayList<>(), new ArrayList<>());
    }

    public ChatRoom(String id, String title, String type, int avatarResId, boolean active,
                    String inactiveStatusLabel, List<ChatMessage> messages,
                    String ownerUsername, List<String> members, List<String> pendingMembers) {
        this(id, title, null, null, type, avatarResId, active, inactiveStatusLabel, messages, ownerUsername, members, pendingMembers);
    }

    public ChatRoom(String id, String title, String subtitle, String postStatusLabel,
                    String type, int avatarResId, boolean active,
                    String inactiveStatusLabel, List<ChatMessage> messages,
                    String ownerUsername, List<String> members, List<String> pendingMembers) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.postStatusLabel = postStatusLabel;
        this.type = type;
        this.avatarResId = avatarResId;
        this.active = active;
        this.inactiveStatusLabel = inactiveStatusLabel == null ? "" : inactiveStatusLabel;
        this.messages = new ArrayList<>(messages);
        this.ownerUsername = ownerUsername;
        this.members = members != null ? new ArrayList<>(members) : new ArrayList<>();
        this.pendingMembers = pendingMembers != null ? new ArrayList<>(pendingMembers) : new ArrayList<>();
        this.lastActivityTime = System.currentTimeMillis();
    }

    // Avatar URL from server
    private String avatarUrl;
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    private boolean friend = true;
    private boolean messageRequest = false;
    private String strangerRequestStatus = null; // null | PENDING | ACCEPTED | REJECTED
    private long otherUserId = -1;
    private String otherUserName = "";
    private String otherUserAvatarUrl = "";
    private int requestCount = 0;
    private boolean blockedByMe = false;
    private boolean hasBlockedMe = false;
    private boolean blockedBetweenUsers = false;

    public boolean isFriend() { return friend; }
    public void setFriend(boolean friend) { this.friend = friend; }
    public boolean isMessageRequest() { return messageRequest; }
    public void setMessageRequest(boolean messageRequest) { this.messageRequest = messageRequest; }
    public String getStrangerRequestStatus() { return strangerRequestStatus; }
    public void setStrangerRequestStatus(String s) { this.strangerRequestStatus = s; }
    public long getOtherUserId() { return otherUserId; }
    public void setOtherUserId(long otherUserId) { this.otherUserId = otherUserId; }
    public String getOtherUserName() { return otherUserName; }
    public void setOtherUserName(String otherUserName) { this.otherUserName = otherUserName != null ? otherUserName : ""; }
    public String getOtherUserAvatarUrl() { return otherUserAvatarUrl; }
    public void setOtherUserAvatarUrl(String otherUserAvatarUrl) {
        this.otherUserAvatarUrl = otherUserAvatarUrl != null ? otherUserAvatarUrl : "";
    }
    public int getRequestCount() { return requestCount; }
    public void setRequestCount(int requestCount) { this.requestCount = Math.max(0, requestCount); }
    public boolean isBlockedByMe() { return blockedByMe; }
    public void setBlockedByMe(boolean blockedByMe) { this.blockedByMe = blockedByMe; }
    public boolean hasBlockedMe() { return hasBlockedMe; }
    public void setHasBlockedMe(boolean hasBlockedMe) { this.hasBlockedMe = hasBlockedMe; }
    public boolean isBlockedBetweenUsers() { return blockedBetweenUsers; }
    public void setBlockedBetweenUsers(boolean blockedBetweenUsers) { this.blockedBetweenUsers = blockedBetweenUsers; }

    // Raw ISO timestamp from API (e.g. "2026-04-18T21:04:10") — dùng để sort danh sách chat
    private String lastMessageTimeRaw = "";
    public String getLastMessageTimeRaw() { return lastMessageTimeRaw != null ? lastMessageTimeRaw : ""; }
    public void setLastMessageTimeRaw(String t) { this.lastMessageTimeRaw = t != null ? t : ""; }

    // ISO timestamp khi phòng được tạo — fallback sort khi chưa có tin nhắn
    private String createdAt = "";
    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String t) { this.createdAt = t != null ? t : ""; }

    // Số tin chưa đọc trong phòng (0 = đã đọc hết)
    private int unreadCount = 0;
    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = Math.max(0, unreadCount); }

    // Ngày diễn ra hoạt động (chỉ dùng cho activity rooms)
    private String activityDateDisplay = null;
    public String getActivityDateDisplay() { return activityDateDisplay; }
    public void setActivityDateDisplay(String activityDateDisplay) { this.activityDateDisplay = activityDateDisplay; }

    // Số thành viên tối đa (từ post.maxMembers, chỉ dùng cho activity rooms)
    private int maxMembers = 0;
    public int getMaxMembers() { return maxMembers; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = Math.max(0, maxMembers); }

    // Trạng thái hoạt động của người đối diện (chỉ dùng trong direct room)
    private boolean otherUserOnline = false;
    private Long otherUserLastActiveMins = null;
    public boolean isOtherUserOnline() { return otherUserOnline; }
    public void setOtherUserOnline(boolean otherUserOnline) { this.otherUserOnline = otherUserOnline; }
    public Long getOtherUserLastActiveMins() { return otherUserLastActiveMins; }
    public void setOtherUserLastActiveMins(Long mins) { this.otherUserLastActiveMins = mins; }

    // Trạng thái khóa/cấm tài khoản của người đối diện (chỉ dùng trong direct room)
    private boolean otherUserBanned = false;
    private boolean otherUserLockedTemp = false;
    private String otherUserLockUntil = null;
    public boolean isOtherUserBanned() { return otherUserBanned; }
    public void setOtherUserBanned(boolean b) { this.otherUserBanned = b; }
    public boolean isOtherUserLockedTemp() { return otherUserLockedTemp; }
    public void setOtherUserLockedTemp(boolean b) { this.otherUserLockedTemp = b; }
    public String getOtherUserLockUntil() { return otherUserLockUntil; }
    public void setOtherUserLockUntil(String s) { this.otherUserLockUntil = s; }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getPostStatusLabel() {
        return postStatusLabel;
    }

    public String getType() {
        return type;
    }

    public int getAvatarResId() {
        return avatarResId;
    }

    public boolean isActive() {
        return active;
    }

    public String getInactiveStatusLabel() {
        return inactiveStatusLabel;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public List<String> getMembers() {
        return members;
    }

    public List<String> getPendingMembers() {
        return pendingMembers;
    }

    public boolean isOwner(String username) {
        return ownerUsername != null && ownerUsername.equalsIgnoreCase(username);
    }

    public boolean isMember(String username) {
        for (String member : members) {
            if (member.equalsIgnoreCase(username)) return true;
        }
        return isOwner(username);
    }

    public boolean isPending(String username) {
        for (String pending : pendingMembers) {
            if (pending.equalsIgnoreCase(username)) return true;
        }
        return false;
    }

    public void addMember(String username) {
        if (!isMember(username)) {
            members.add(username);
        }
        pendingMembers.removeIf(p -> p.equalsIgnoreCase(username));
    }

    public void removeMember(String username) {
        members.removeIf(m -> m.equalsIgnoreCase(username));
    }

    public void addPendingMember(String username) {
        if (!isMember(username) && !isPending(username)) {
            pendingMembers.add(username);
        }
    }

    public void rejectPendingMember(String username) {
        pendingMembers.removeIf(p -> p.equalsIgnoreCase(username));
    }

    public String getLastMessagePreview() {
        if (messages.isEmpty()) {
            return "No messages yet";
        }
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        return lastMessage.getContent();
    }

    public String getLastMessageTime() {
        if (messages.isEmpty()) {
            return "";
        }
        return messages.get(messages.size() - 1).getTimeLabel();
    }

    public String getTypeLabel() {
        if (TYPE_GROUP.equals(type) || TYPE_ACTIVITY.equals(type)) {
            return "Hoạt động";
        }
        if (TYPE_FRIEND_GROUP.equals(type)) {
            return "Nhóm bạn bè";
        }
        return "Liên hệ";
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        this.lastActivityTime = System.currentTimeMillis();
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }
}
