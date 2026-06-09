package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    public enum NotificationType {
        FRIEND_REQUEST_RECEIVED,
        FRIEND_REQUEST_SENT,
        FRIEND_ACCEPTED,
        JOIN_REQUEST,
        JOIN_APPROVED,
        JOIN_REJECTED,
        ADMIN_WARNING,
        ADMIN_ACTION,
        REPORT_CONFIRMED,
        REPORT_PENALTY,
        POST_EXPIRED,
        ACTIVITY_CANCELLED,
        CHAT_SUMMARY,
        STRANGER_REQUEST_ACCEPTED,
        GENERAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, columnDefinition = "VARCHAR(50)")
    private NotificationType type;

    @Column(length = 500)
    private String message;

    private String relatedUsername;

    @Column(name = "related_post_id")
    private Long relatedPostId;

    @Column(name = "related_user_id")
    private Long relatedUserId;

    @Column(name = "related_report_id")
    private Long relatedReportId;

    @Column(name = "related_room_id")
    private Long relatedRoomId;

    @Column(name = "is_read", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isRead;

    @Column(name = "is_actioned", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isActioned;

    @Column(name = "action_result", length = 20)
    private String actionResult;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
