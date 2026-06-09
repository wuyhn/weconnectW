package com.weconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String fullName;
    private String birthday;
    private String gender;
    private String provinceId;
    private String provinceName;
    private String avatarUrl;
    private String bio;
    private String interestTags;
    private float averageRating;
    private double reputationScore;
    private int totalReviewCount;
    private boolean isActivityJoinLocked;
    private boolean isBlockedByMe;
    private boolean hasBlockedMe;
    private boolean isBlockedBetweenUsers;

    public boolean getIsBlockedByMe() {
        return isBlockedByMe;
    }

    public boolean getHasBlockedMe() {
        return hasBlockedMe;
    }

    public boolean getIsBlockedBetweenUsers() {
        return isBlockedBetweenUsers;
    }
}
