package com.weconnect.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Boolean isFriend;
    private Boolean isMessageRequest;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserAvatarUrl;
    private Boolean isBlockedByMe;
    private Boolean hasBlockedMe;
    private Boolean isBlockedBetweenUsers;
    private Boolean otherUserOnline;
    private Long otherUserLastActiveMins;
    private String strangerRequestStatus; // null | PENDING | ACCEPTED | REJECTED
    private Boolean isOtherUserBanned;     // tài khoản bị cấm vĩnh viễn
    private Boolean isOtherUserLockedTemp; // tài khoản bị khóa tạm thời
    private String otherUserLockUntil;     // ngày mở khóa (dd/MM/yyyy HH:mm), nullable
    private int unreadCount;
    private String activityDateDisplay;
    private int memberCount;
    private int maxMembers;
    private List<MemberInfo> members;
    private String createdAt;

    public Boolean getIsFriend() {
        return isFriend;
    }

    public Boolean getIsMessageRequest() {
        return isMessageRequest;
    }

    public Boolean getIsBlockedByMe() {
        return isBlockedByMe;
    }

    public Boolean getHasBlockedMe() {
        return hasBlockedMe;
    }

    public Boolean getIsBlockedBetweenUsers() {
        return isBlockedBetweenUsers;
    }

    public Boolean getIsOtherUserBanned() {
        return isOtherUserBanned;
    }

    public Boolean getIsOtherUserLockedTemp() {
        return isOtherUserLockedTemp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberInfo {
        private Long id;
        private String fullName;
        private String role;
        private String avatarUrl;
        private Boolean isBlockedByMe;
        private Boolean hasBlockedMe;
        private Boolean isBlockedBetweenUsers;
    }
}
