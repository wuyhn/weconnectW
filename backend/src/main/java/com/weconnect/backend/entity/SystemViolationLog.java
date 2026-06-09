package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lưu vết từng lần phạt tự động (AI, wrong-tag...) hoặc tha phạt (hết hạn khóa).
 * Thay thế trường violationPenaltySum trên User (cộng dồn không tái tính được).
 *
 * Quy ước:
 *   penaltyPoint > 0  → phạt
 *   penaltyPoint < 0  → tha phạt (forgiveness khi khóa hết hạn)
 */
@Entity
@Table(name = "system_violation_logs", indexes = {
        @Index(name = "idx_svl_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemViolationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // Ví dụ: WRONG_TAG, AUTO_VIOLATION, LOCK_EXPIRY_FORGIVENESS
    @Column(name = "violation_type", nullable = false, length = 50)
    private String violationType;

    // Dương = phạt, Âm = tha phạt (giảm tổng penalty khi tái tính)
    @Column(name = "penalty_point", nullable = false)
    private int penaltyPoint;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
