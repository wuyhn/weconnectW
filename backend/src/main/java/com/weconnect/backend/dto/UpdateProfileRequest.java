package com.weconnect.backend.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String birthday;
    private String gender;
    private String bio;
    private String interestTags;
}
