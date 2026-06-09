package com.weconnect.backend.dto;

/**
 * DTO nhận yêu cầu đặt lại mật khẩu từ Android client.
 * Gồm email định danh tài khoản, mã OTP xác thực và mật khẩu mới.
 */
public class ResetPasswordRequest {

    private String email;
    private String otpCode;
    private String newPassword;

    public ResetPasswordRequest() {}

    public ResetPasswordRequest(String email, String otpCode, String newPassword) {
        this.email = email;
        this.otpCode = otpCode;
        this.newPassword = newPassword;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtpCode() { return otpCode; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
