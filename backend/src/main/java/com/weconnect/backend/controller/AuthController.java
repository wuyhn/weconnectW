package com.weconnect.backend.controller;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
import com.weconnect.backend.dto.LockedAccountResponse;
import com.weconnect.backend.dto.LogoutRequest;
import com.weconnect.backend.dto.RefreshTokenRequest;
import com.weconnect.backend.dto.RegisterRequest;
import com.weconnect.backend.dto.ResendOtpRequest;
import com.weconnect.backend.dto.ResetPasswordRequest;
import com.weconnect.backend.dto.VerifyOtpRequest;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.entity.User;
import com.weconnect.backend.exception.AccountSanctionException;
import com.weconnect.backend.exception.LockedAccountException;
import com.weconnect.backend.exception.UserNotFoundException;
import com.weconnect.backend.service.AuthService;
import com.weconnect.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        try {
            String result = authService.register(request);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1001)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody VerifyOtpRequest request) {
        try {
            AuthResponse response = authService.verifyOtp(request.getEmail(), request.getOtpCode());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(response.getMessage())
                    .result(response)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1003)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<?> resendOtp(@RequestBody ResendOtpRequest request) {
        try {
            String result = authService.resendOtp(request.getEmail());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1004)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            AuthResponse response = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message("Đăng nhập thành công!")
                    .result(response)
                    .build());
        } catch (LockedAccountException e) {
            // Kịch bản 3: Tài khoản LOCKED_TEMP cố đăng nhập — HTTP 423 kèm ngày mở khóa.
            // Android LoginActivity sẽ bắt code 423 và hiển thị Toast với lockUntil.
            return ResponseEntity.status(423).body(ApiResponse.builder()
                    .code(1007)
                    .message(e.getMessage())
                    .result(LockedAccountResponse.builder().lockUntil(e.getLockUntil()).build())
                    .build());
        } catch (AccountSanctionException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1006)
                    .message(e.getMessage())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(ApiResponse.builder()
                    .code(1002)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse response = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(response.getMessage())
                    .result(response)
                    .build());
        } catch (LockedAccountException e) {
            return ResponseEntity.status(423).body(ApiResponse.builder()
                    .code(1007)
                    .message(e.getMessage())
                    .result(LockedAccountResponse.builder().lockUntil(e.getLockUntil()).build())
                    .build());
        } catch (AccountSanctionException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1006)
                    .message(e.getMessage())
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(ApiResponse.builder()
                    .code(1008)
                    .message(e.getMessage())
                    .build());
        }
    }

    // ---------------------------------------------------------------
    // Luồng quên mật khẩu — Endpoint 1: POST /api/auth/forgot-password?email=...
    // ---------------------------------------------------------------

    /**
     * Nhận email qua query param, sinh OTP 6 số, lưu DB và gửi email HTML cho người dùng.
     * Trả về HTTP 404 nếu email chưa đăng ký, 400 nếu lỗi gửi mail, 200 nếu thành công.
     * Endpoint nằm trong /api/auth/** nên không cần JWT (đã permitAll trong SecurityConfig).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            String result = authService.forgotPassword(email);
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        } catch (UserNotFoundException e) {
            // Email không tồn tại trong hệ thống → HTTP 404
            return ResponseEntity.status(404).body(ApiResponse.builder()
                    .code(1005)
                    .message(e.getMessage())
                    .build());
        } catch (RuntimeException e) {
            // Lỗi gửi mail hoặc lỗi khác → HTTP 400
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1001)
                    .message(e.getMessage())
                    .build());
        }
    }

    // ---------------------------------------------------------------
    // Luồng quên mật khẩu — Endpoint 2: POST /api/auth/reset-password
    // ---------------------------------------------------------------

    /**
     * Nhận email + otpCode + newPassword, xác thực OTP rồi cập nhật mật khẩu mới (BCrypt).
     * Trả về HTTP 400 nếu OTP sai/hết hạn hoặc mật khẩu không hợp lệ, 200 nếu thành công.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            String result = authService.resetPassword(
                    request.getEmail(),
                    request.getOtpCode(),
                    request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.builder()
                    .code(1001)
                    .message(e.getMessage())
                    .build());
        }
    }

    // Hủy liên kết FCM token của thiết bị khi người dùng đăng xuất.
    // Endpoint nằm trong /api/auth/** (permitAll) nhưng JWT filter vẫn chạy
    // và set SecurityContext — principal sẽ có giá trị nếu JWT còn hợp lệ.
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @AuthenticationPrincipal User currentUser,
            @RequestBody LogoutRequest request) {
        if (currentUser != null && request != null) {
            userService.removeFcmToken(currentUser.getId(), request.getFcmToken());
        }
        return ResponseEntity.ok(ApiResponse.builder()
                .code(1000)
                .message("Đăng xuất thành công!")
                .build());
    }
}
