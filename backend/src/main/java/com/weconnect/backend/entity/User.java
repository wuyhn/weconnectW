package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String birthday;
    private String gender;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 1000)
    private String bio;

    @Column(length = 500)
    private String interestTags; // Comma-separated: "Cà phê,Lập trình,Ẩm thực"

    @Column(columnDefinition = "FLOAT DEFAULT 0")
    private float averageRating;

    @Builder.Default
    @Column(columnDefinition = "DOUBLE DEFAULT 100")
    private double reputationScore = 100;

    @Column(name = "is_blocked")
    private boolean isBlocked = false;

    // 0 = User thường, 1 = Admin
    @Column(columnDefinition = "INT DEFAULT 0")
    private int role = 0;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(name = "activity_status_enabled", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean activityStatusEnabled = true;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
