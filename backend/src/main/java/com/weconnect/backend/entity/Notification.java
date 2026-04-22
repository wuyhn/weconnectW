package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
        POST_EXPIRED,
        GENERAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(length = 500)
    private String message;

    private String relatedUsername;

    @Column(name = "related_post_id")
    private Long relatedPostId;

    @Column(name = "related_user_id")
    private Long relatedUserId;

    @Column(name = "is_read", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isRead;

    @Column(name = "is_actioned", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean isActioned;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
