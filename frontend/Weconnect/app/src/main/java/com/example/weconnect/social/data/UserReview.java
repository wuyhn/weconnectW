package com.example.weconnect.social.data;

import java.io.Serializable;

public class UserReview implements Serializable {
    private String reviewerName;
    private String activityName;
    private String reputationLabel;
    private String comment;

    public UserReview(String reviewerName, String activityName, String reputationLabel, String comment) {
        this.reviewerName = reviewerName;
        this.activityName = activityName;
        this.reputationLabel = reputationLabel;
        this.comment = comment;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getReputationLabel() {
        return reputationLabel;
    }

    public String getComment() {
        return comment;
    }
}