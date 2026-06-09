package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.AuthResponse;
import com.example.weconnect.models.LoginRequest;
import com.example.weconnect.models.LogoutRequest;
import com.example.weconnect.models.ResendOtpRequest;
import com.example.weconnect.models.ResetPasswordRequest;
import com.example.weconnect.models.User;
import com.example.weconnect.models.VerifyOtpRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface AuthApiService {

    @POST("api/auth/login")
    Call<ApiResponse<AuthResponse>> login(@Body LoginRequest loginRequest);

    @POST("api/auth/register")
    Call<ApiResponse<Void>> register(@Body User user);

    @POST("api/auth/verify-otp")
    Call<ApiResponse<AuthResponse>> verifyOtp(@Body VerifyOtpRequest request);

    @POST("api/auth/resend-otp")
    Call<ApiResponse<Void>> resendOtp(@Body ResendOtpRequest request);

    @POST("api/auth/logout")
    Call<ApiResponse<Void>> logout(@Body LogoutRequest request);

    /**
     * Bước 1 quên mật khẩu: gửi email kèm OTP về hộp thư người dùng.
     * Email truyền qua query param: POST /api/auth/forgot-password?email=...
     *
     * @param email địa chỉ email đã đăng ký
     */
    @POST("api/auth/forgot-password")
    Call<ApiResponse<Void>> forgotPassword(@Query("email") String email);

    /**
     * Bước 2 quên mật khẩu: xác thực OTP và đặt lại mật khẩu mới.
     * Body JSON: {"email":"...","otpCode":"...","newPassword":"..."}
     *
     * @param request chứa email, otpCode, newPassword
     */
    @POST("api/auth/reset-password")
    Call<ApiResponse<Void>> resetPassword(@Body ResetPasswordRequest request);
}
