package com.example.weconnect.auth.data;

/**
 * Auth response từ backend khi login.
 * Chứa JWT token + thông tin user cơ bản.
 */
public class AuthResponse {
    private Long id;
    private String email;
    private String fullName;
    private String token;
    private String message;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
