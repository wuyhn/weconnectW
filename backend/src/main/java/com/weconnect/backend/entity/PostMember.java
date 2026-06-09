package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMember {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        // Tự động từ chối khi Host bị khóa trong lúc hoạt động đang diễn ra
        REJECTED_BY_SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "join_reason", length = 500)
    private String joinReason;

    @Column(name = "requester_province", length = 100)
    private String requesterProvince;

    @Column(name = "activity_province", length = 100)
    private String activityProvince;

    @Column(name = "is_far_location")
    private Boolean isFarLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
}
