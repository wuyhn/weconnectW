package com.weconnect.backend.controller;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
import com.weconnect.backend.dto.ResendOtpRequest;
import com.weconnect.backend.dto.VerifyOtpRequest;
import com.weconnect.backend.dto.request.ApiResponse;
import com.weconnect.backend.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            String result = authService.register(request);
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
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(ApiResponse.builder()
                    .code(1002)
                    .message(e.getMessage())
                    .build());
        }
    }
}
