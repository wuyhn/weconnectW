package com.weconnect.backend.controller;

import com.weconnect.backend.dto.AuthRequest;
import com.weconnect.backend.dto.AuthResponse;
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
        String result = authService.register(request);
        if (result.contains("thành công")) {
            return ResponseEntity.ok(ApiResponse.builder()
                    .code(1000)
                    .message(result)
                    .build());
        }
        return ResponseEntity.badRequest().body(ApiResponse.builder()
                .code(1001)
                .message(result)
                .build());
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
