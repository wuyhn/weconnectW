package com.weconnect.backend.repository;

import com.weconnect.backend.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    /** Tìm OTP đang active theo email */
    Optional<PasswordResetOtp> findByEmail(String email);

    /** Xóa OTP theo email (dùng JPQL bulk-delete để tránh SELECT trước khi DELETE) */
    @Modifying
    @Query("DELETE FROM PasswordResetOtp p WHERE p.email = :email")
    void deleteByEmail(@Param("email") String email);
}
