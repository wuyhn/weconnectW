package com.weconnect.backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PostRequest {
    private String content;
    private String interestTag;
    private String location;
    private String imageUrl;
    private int maxMembers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer expirationHours;
}
