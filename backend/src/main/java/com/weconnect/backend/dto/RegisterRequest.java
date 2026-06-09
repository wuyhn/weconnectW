package com.weconnect.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RegisterRequest {

    public static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$";
    public static final String PASSWORD_SECURITY_MESSAGE =
            "Mật khẩu phải chứa ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt!";

    @NotBlank(message = "Email không được để trống.")
    @Email(message = "Email không đúng định dạng.")
    private String email;

    // Regex dùng positive lookahead để bắt buộc mật khẩu có đủ chữ hoa, chữ thường, chữ số,
    // ký tự đặc biệt không phải khoảng trắng và tổng độ dài tối thiểu 8 ký tự.
    @NotBlank(message = "Mật khẩu không được để trống.")
    @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_SECURITY_MESSAGE)
    private String password;
}
