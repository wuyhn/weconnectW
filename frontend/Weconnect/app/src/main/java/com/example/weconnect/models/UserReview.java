package com.example.weconnect.models;

import java.io.Serializable;

public class UserReview implements Serializable {
    private long id;
    private long reviewerId;
    private long reviewedUserId;
    private long postId;

    private String reviewerName;
    private String reviewerAvatarUrl;

    private Integer rating;
    private String activityName;
    private String interestTag;
    private String activityDateDisplay;  // "Đã cùng tham gia: Tag - dd/MM/yyyy"
    private String reputationLabel;
    private String comment;

    private String createdAt;
    private String updatedAt;
    private boolean isEdited;

    public UserReview() {}

    // Constructor tương thích ngược cho code cũ
    public UserReview(String reviewerName, String activityName, String reputationLabel, String comment) {
        this.reviewerName = reviewerName;
        this.activityName = activityName;
        this.reputationLabel = reputationLabel;
        this.comment = comment;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getReviewerId() { return reviewerId; }
    public void setReviewerId(long reviewerId) { this.reviewerId = reviewerId; }

    public long getReviewedUserId() { return reviewedUserId; }
    public void setReviewedUserId(long reviewedUserId) { this.reviewedUserId = reviewedUserId; }

    public long getPostId() { return postId; }
    public void setPostId(long postId) { this.postId = postId; }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getReviewerAvatarUrl() { return reviewerAvatarUrl; }
    public void setReviewerAvatarUrl(String reviewerAvatarUrl) { this.reviewerAvatarUrl = reviewerAvatarUrl; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }

    public String getInterestTag() { return interestTag; }
    public void setInterestTag(String interestTag) { this.interestTag = interestTag; }

    public String getActivityDateDisplay() { return activityDateDisplay; }
    public void setActivityDateDisplay(String activityDateDisplay) { this.activityDateDisplay = activityDateDisplay; }

    public String getReputationLabel() { return reputationLabel; }
    public void setReputationLabel(String reputationLabel) { this.reputationLabel = reputationLabel; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isEdited() { return isEdited; }
    public void setEdited(boolean edited) { isEdited = edited; }
}
