package com.example.weconnect.models;

public class SearchResultItem {

    public static final int TYPE_SECTION = 0;
    public static final int TYPE_USER = 1;
    public static final int TYPE_POST = 2;

    private int viewType;
    private String title;
    private String subtitle;
    private int avatarResId;
    private long userId; // Backend user ID
    private boolean blockedByMe;
    private boolean hasBlockedMe;
    private boolean blockedBetweenUsers;

    private String username;
    private String content;
    private String tag;
    private String location;
    private int memberCount;
    private int maxMembers;
    private Post post;

    public SearchResultItem(int viewType, String title, String subtitle, int avatarResId) {
        this.viewType = viewType;
        this.title = title;
        this.subtitle = subtitle;
        this.avatarResId = avatarResId;
    }

    public SearchResultItem(int viewType, String title, String subtitle, int avatarResId,
                            String username, String content, String tag, String location,
                            int memberCount, int maxMembers, Post post) {
        this.viewType = viewType;
        this.title = title;
        this.subtitle = subtitle;
        this.avatarResId = avatarResId;
        this.username = username;
        this.content = content;
        this.tag = tag;
        this.location = location;
        this.memberCount = memberCount;
        this.maxMembers = maxMembers;
        this.post = post;
    }

    public int getViewType() {
        return viewType;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public int getAvatarResId() {
        return avatarResId;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }

    public String getTag() {
        return tag;
    }

    public String getLocation() {
        return location;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public Post getPost() {
        return post;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public boolean isBlockedByMe() {
        return blockedByMe;
    }

    public void setBlockedByMe(boolean blockedByMe) {
        this.blockedByMe = blockedByMe;
    }

    public boolean hasBlockedMe() {
        return hasBlockedMe;
    }

    public void setHasBlockedMe(boolean hasBlockedMe) {
        this.hasBlockedMe = hasBlockedMe;
    }

    public boolean isBlockedBetweenUsers() {
        return blockedBetweenUsers;
    }

    public void setBlockedBetweenUsers(boolean blockedBetweenUsers) {
        this.blockedBetweenUsers = blockedBetweenUsers;
    }

    // Avatar URL from server
    private String avatarUrl;
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
}
