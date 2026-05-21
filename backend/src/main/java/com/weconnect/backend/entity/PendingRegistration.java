package com.weconnect.backend.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "pending_registrations")
@Data
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String passwordEncoded;

    @Column(nullable = false, length = 6)
    private String otpCode;

    private LocalDateTime otpExpiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime lastSentAt;

    private int attemptCount;
}
