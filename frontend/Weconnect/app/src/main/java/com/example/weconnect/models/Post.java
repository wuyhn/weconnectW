package com.example.weconnect.models;

public class Post implements java.io.Serializable {
    private String id;
    private String username;
    private String timeAgo;
    private String content;
    private String interestTag;
    private int avatarResId;
    private int imageResId;
    private int memberCount;
    private int likesCount;
    private int commentsCount;
    private int maxMembers;
    private boolean joined;
    private boolean pendingApproval;
    private String location;
    private long startTimeMillis;
    private long endTimeMillis;
    private boolean archived;
    private boolean cancelled;
    private long authorId;
    private int expirationHours;

    public Post(String id, String username, String timeAgo, String content, String interestTag, String location,
                int avatarResId, int imageResId, int memberCount, int likesCount,
                int commentsCount, int maxMembers, boolean joined) {
        this(id, username, timeAgo, content, interestTag, location, avatarResId, imageResId,
                memberCount, likesCount, commentsCount, maxMembers, joined,
                System.currentTimeMillis(), System.currentTimeMillis() + 24L * 60L * 60L * 1000L, false);
    }

    public Post(String id, String username, String timeAgo, String content, String interestTag, String location,
                int avatarResId, int imageResId, int memberCount, int likesCount,
                int commentsCount, int maxMembers, boolean joined, long startTimeMillis,
                long endTimeMillis, boolean archived) {
        this.id = id;
        this.username = username;
        this.timeAgo = timeAgo;
        this.content = content;
        this.interestTag = interestTag;
        this.avatarResId = avatarResId;
        this.imageResId = imageResId;
        this.memberCount = memberCount;
        this.likesCount = likesCount;
        this.commentsCount = commentsCount;
        this.maxMembers = maxMembers;
        this.joined = joined;
        this.location = location;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.archived = archived;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getTimeAgo() { return timeAgo; }
    public String getContent() { return content; }
    public String getInterestTag() { return interestTag; }
    public String getLocation() { return location; }
    public int getAvatarResId() { return avatarResId; }
    public int getImageResId() { return imageResId; }
    public int getMemberCount() { return memberCount; }
    public int getLikesCount() { return likesCount; }
    public int getCommentsCount() { return commentsCount; }
    public int getMaxMembers() { return maxMembers; }
    public boolean isJoined() { return joined; }
    public boolean isPendingApproval() { return pendingApproval; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public boolean isArchived() { return archived; }
    public boolean isCancelled() { return cancelled; }

    public boolean isExpired() {
        return System.currentTimeMillis() > endTimeMillis;
    }

    public boolean isActive() {
        return !archived && !cancelled && !isExpired();
    }

    public String getStatusLabel() {
        if (cancelled) return "Đã hủy";
        if (archived || isExpired()) return "Archived";
        return "Active";
    }

    public void setMemberCount(int memberCount) { this.memberCount = memberCount; }
    public void setMaxMembers(int maxMembers) { this.maxMembers = maxMembers; }
    public void setJoined(boolean joined) { this.joined = joined; }
    public void setPendingApproval(boolean pendingApproval) { this.pendingApproval = pendingApproval; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public void setContent(String content) { this.content = content; }
    public void setLocation(String location) { this.location = location; }
    public void setInterestTag(String tag) { this.interestTag = tag; }

    // User-uploaded image URI (from gallery)
    private String postImageUri;
    public String getPostImageUri() { return postImageUri; }
    public void setPostImageUri(String uri) { this.postImageUri = uri; }

    public long getAuthorId() { return authorId; }
    public void setAuthorId(long authorId) { this.authorId = authorId; }
    public int getExpirationHours() { return expirationHours; }
    public void setExpirationHours(int expirationHours) { this.expirationHours = expirationHours; }

    // Avatar URL from server
    private String avatarUrl;
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    // Formatted posted date, e.g. "12/05/2026"
    private String postedDate;
    public String getPostedDate() { return postedDate; }
    public void setPostedDate(String postedDate) { this.postedDate = postedDate; }

    // ISO string of activity end time (e.g. "2026-06-01T10:00:00")
    private String activityEndTimeStr;
    public String getActivityEndTimeStr() { return activityEndTimeStr; }
    public void setActivityEndTimeStr(String s) { this.activityEndTimeStr = s; }

    // "DAILY_TIME_SLOT" or "CONTINUOUS_RANGE"
    private String activityTimeType;
    public String getActivityTimeType() { return activityTimeType; }
    public void setActivityTimeType(String t) { this.activityTimeType = t; }
}
