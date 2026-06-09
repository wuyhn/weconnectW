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
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_LOCKED_TEMP = "LOCKED_TEMP";
    public static final String STATUS_BANNED = "BANNED";

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

    @Column(length = 20)
    private String provinceId;

    @Column(length = 100)
    private String provinceName;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 1000)
    private String bio;

    // Sở thích CHỦ ĐỘNG: 5 tags user tự chọn lúc đăng ký — hiển thị trên Profile, không bao giờ tự thay đổi
    @Column(length = 500)
    private String interestTags; // Comma-separated: "Cà phê,Lập trình,Ẩm thực"

    // Sở thích ẨN học từ hành vi: Backend tự ghi nhận khi user tham gia hoạt động có tag mới
    // Tối đa 5 tags. KHÔNG MAP vào UserProfileResponse → Profile không bị ảnh hưởng
    @Column(name = "behavioral_tags", length = 500)
    private String behavioralTags;

    @Column(columnDefinition = "FLOAT DEFAULT 0")
    private float averageRating;

    @Builder.Default
    @Column(columnDefinition = "DOUBLE DEFAULT 60")
    private double reputationScore = 60;

    @Builder.Default
    @Column(name = "violation_penalty_sum", columnDefinition = "INT DEFAULT 0")
    private int violationPenaltySum = 0;

    @Builder.Default
    @Column(name = "violation_count", columnDefinition = "INT DEFAULT 0")
    private int violationCount = 0;

    @Builder.Default
    @Column(name = "status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
    private String status = STATUS_ACTIVE;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    // Tổng điểm phạt tích lũy từ admin (warn, block, non-attendance, v.v.)
    // Lưu riêng để recalculateReputation() có thể trừ ra khỏi rating component
    @Builder.Default
    @Column(columnDefinition = "DOUBLE DEFAULT 0")
    private double adminPenalty = 0;

    @Builder.Default
    @Column(name = "is_blocked")
    private boolean isBlocked = false;

    // 0 = User thường, 1 = Admin
    @Builder.Default
    @Column(columnDefinition = "INT DEFAULT 0")
    private int role = 0;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Builder.Default
    @Column(name = "activity_status_enabled", columnDefinition = "BOOLEAN DEFAULT TRUE")
    private Boolean activityStatusEnabled = true;

    @PrePersist
    protected void onCreate() {
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (status == null || status.isBlank()) {
            status = STATUS_ACTIVE;
        }
    }
}
