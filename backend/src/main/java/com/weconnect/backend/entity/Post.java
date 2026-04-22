package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String interestTag;

    @Column(length = 500)
    private String location;

    @Column(length = 500)
    private String imageUrl;

    @Column(columnDefinition = "INT DEFAULT 10")
    private int maxMembers;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Column(name = "expiration_hours")
    private Integer expirationHours;

    @Column(columnDefinition = "BOOLEAN DEFAULT false")
    private boolean archived;

    @Column(name = "expiration_notified", columnDefinition = "BOOLEAN DEFAULT false")
    private boolean expirationNotified;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return endTime != null && LocalDateTime.now().isAfter(endTime);
    }

    public boolean isActive() {
        return !archived && !isExpired();
    }
}
