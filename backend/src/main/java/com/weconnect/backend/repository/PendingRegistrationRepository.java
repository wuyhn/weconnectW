package com.weconnect.backend.repository;

import com.weconnect.backend.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByEmail(String email);

    void deleteByEmail(String email);

    void deleteByOtpExpiresAtBefore(LocalDateTime now);
}
