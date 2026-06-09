package com.weconnect.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Lưu mã OTP tạm thời phục vụ luồng đặt lại mật khẩu.
 * Mỗi email chỉ có một bản ghi tại một thời điểm (ràng buộc unique).
 * Bản ghi bị xóa ngay sau khi người dùng đặt lại thành công hoặc tạo OTP mới.
 */
@Entity
@Table(name = "password_reset_otps")
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email của người dùng — duy nhất, mỗi email chỉ có 1 OTP reset đang active */
    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Mã OTP ngẫu nhiên 6 chữ số */
    @Column(name = "otp_code", nullable = false, length = 6)
    private String otpCode;

    /** Thời điểm hết hạn (thường là 5 phút kể từ lúc tạo) */
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /** Thời điểm tạo bản ghi, dùng để log và debug */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
