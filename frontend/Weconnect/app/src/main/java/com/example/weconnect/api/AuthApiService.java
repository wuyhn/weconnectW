package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.AuthResponse;
import com.example.weconnect.models.LoginRequest;
import com.example.weconnect.models.ResendOtpRequest;
import com.example.weconnect.models.User;
import com.example.weconnect.models.VerifyOtpRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface AuthApiService {

    @POST("api/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest loginRequest);

    @POST("api/auth/register")
    Call<ApiResponse<Void>> register(@Body User user);

    @POST("api/auth/verify-otp")
    Call<ApiResponse<AuthResponse>> verifyOtp(@Body VerifyOtpRequest request);

    @POST("api/auth/resend-otp")
    Call<ApiResponse<Void>> resendOtp(@Body ResendOtpRequest request);
}
