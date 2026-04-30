package com.example.weconnect.auth.data;

import com.example.weconnect.core.data.ApiResponse;
import retrofit2.Callback;

public class AuthRepository {
    private AuthApiService apiService;

    public AuthRepository(AuthApiService apiService) {
        this.apiService = apiService;
    }

    public void login(String email, String password, Callback<ApiResponse<AuthResponse>> callback) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        apiService.login(loginRequest).enqueue(callback);
    }
}
