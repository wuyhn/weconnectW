package com.weconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long id;
    private Long authorId;
    private String authorName;
    private String authorAvatarUrl;
    private String content;
    private String interestTag;
    private String location;
    private String imageUrl;
    private int maxMembers;
    private int memberCount;
    private int likesCount;
    private int commentsCount;
    private boolean joined;
    private boolean pendingApproval;
    private boolean archived;
    private boolean expired;
    private Integer expirationHours;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
}
