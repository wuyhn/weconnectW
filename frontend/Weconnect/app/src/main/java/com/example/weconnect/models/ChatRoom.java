package com.example.weconnect.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ChatRoom implements Serializable {

    public static final String TYPE_GROUP = "group";
    public static final String TYPE_DIRECT = "direct";
    public static final String TYPE_FRIEND_GROUP = "friend_group";
    public static final String TYPE_ACTIVITY = "activity";

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
