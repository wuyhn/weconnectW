package com.example.weconnect.auth.data;

import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.auth.data.AuthResponse;
import com.example.weconnect.auth.data.LoginRequest;
import com.example.weconnect.profile.data.User;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest loginRequest);

    @POST("api/auth/register")
    Call<ApiResponse<Void>> register(@Body User user);
}
