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
    private String avatarUrl;
    private String bio;
    private String interestTags;
    private float averageRating;
    private int reputationScore;
}
