package com.example.weconnect.models;

import java.io.Serializable;
import java.util.List;

public class UserProfile implements Serializable {
    private String username;
    private int avatarResId;
    private float averageRating;
    private int reputationScore;
    private List<String> interestTags;
    private List<UserReview> reviews;

    public UserProfile(String username, int avatarResId, float averageRating, int reputationScore, List<String> interestTags, List<UserReview> reviews) {
        this.username = username;
        this.avatarResId = avatarResId;
        this.averageRating = averageRating;
        this.reputationScore = reputationScore;
        this.reviews = reviews;
        this.interestTags = interestTags;
    }

    public String getUsername() {
        return username;
    }

    public int getAvatarResId() {
        return avatarResId;
    }

    public float getAverageRating() {
        return averageRating;
    }

    public int getReputationScore() {
        return reputationScore;
    }

    public List<UserReview> getReviews() {
        return reviews;
    }

    public List<String> getInterestTags() {
        return interestTags;
    }
}