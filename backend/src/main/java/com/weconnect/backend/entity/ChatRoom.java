package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    public static final String TYPE_GROUP = "group";
    public static final String TYPE_DIRECT = "direct";
    public static final String TYPE_FRIEND_GROUP = "friend_group";
    public static final String TYPE_ACTIVITY = "activity";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", unique = true)
    private Long postId; // Liên kết với bài post (chỉ dùng cho TYPE_ACTIVITY)

    @Column(length = 200)
    private String title;

    @Column(length = 50, nullable = false)
    private String type; // group, direct, friend_group

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "is_active", columnDefinition = "BOOLEAN DEFAULT true")
    private boolean active;

    @Column(length = 200)
    private String inactiveStatusLabel;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
