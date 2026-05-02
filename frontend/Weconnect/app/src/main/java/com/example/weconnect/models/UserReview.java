package com.example.weconnect.models;

import java.io.Serializable;

public class UserReview implements Serializable {
    private String reviewerName;
    private String activityName;
    private String reputationLabel;
    private String comment;
    private float rating;

    public UserReview() {}

    public UserReview(String reviewerName, String activityName, String reputationLabel, String comment) {
        this.reviewerName = reviewerName;
        this.activityName = activityName;
        this.reputationLabel = reputationLabel;
        this.comment = comment;
    }

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }

    public String getReputationLabel() { return reputationLabel; }
    public void setReputationLabel(String reputationLabel) { this.reputationLabel = reputationLabel; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
}