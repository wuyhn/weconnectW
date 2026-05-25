package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    public enum TargetType { USER, POST }
    public enum Status { PENDING, VALID, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Builder.Default
    @Column(name = "penalty_point", columnDefinition = "INT DEFAULT 0")
    private Integer penaltyPoint = 0;

    @Column(name = "admin_action", length = 30)
    private String adminAction;

    @Column(name = "admin_viewed", columnDefinition = "BOOLEAN DEFAULT false")
    @Builder.Default
    private boolean adminViewed = false;

    @Column(name = "evidence_images", columnDefinition = "TEXT")
    private String evidenceImages;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
