package com.weconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomResponse {
    private Long id;
    private Long postId;
    private String title;
    private String type;
    private Long ownerId;
    private String ownerName;
    private boolean active;
    private String inactiveStatusLabel;
    private String subtitle;         // e.g., "14:00 · Hồ Gươm"
    private String postStatusLabel;  // e.g., "Hoạt động đã kết thúc" or null
    private String lastMessagePreview;
    private String lastMessageTime;
    private List<MemberInfo> members;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private Long id;
        private String fullName;
        private String role;
        private String avatarUrl;
    }
}
