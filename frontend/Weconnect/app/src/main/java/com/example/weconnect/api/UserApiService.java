package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;


public interface UserApiService {

    // Lưu sở thích từ onboarding
    @PUT("api/users/me/interests")
    Call<ApiResponse<List<String>>> saveInterests(@Body Map<String, List<String>> body);

    // Lấy sở thích đã lưu
    @GET("api/users/me/interests")
    Call<ApiResponse<List<String>>> getInterests();

    // Lấy profile của chính mình
    @GET("api/users/me")
    Call<ApiResponse<Map<String, Object>>> getMyProfile();

    // Cập nhật profile
    @PUT("api/users/me")
    Call<ApiResponse<Map<String, Object>>> updateProfile(@Body Map<String, Object> body);

    // Lấy profile user khác
    @GET("api/users/{id}")
    Call<ApiResponse<Map<String, Object>>> getUserProfile(@Path("id") long id);

    // Gợi ý user có cùng sở thích
    @GET("api/users/suggestions")
    Call<ApiResponse<java.util.List<Map<String, Object>>>> getSuggestions(@Query("excludeId") long excludeId);

    // Xóa tài khoản
    @DELETE("api/users/me")
    Call<ApiResponse<Void>> deleteAccount();

    // Tìm user theo tên
    @GET("api/users/search")
    Call<ApiResponse<Map<String, Object>>> searchByName(@Query("name") String name);

    // Tìm kiếm user partial match
    @GET("api/users/search/partial")
    Call<ApiResponse<List<Map<String, Object>>>> searchUsersPartial(@Query("q") String query);

    // Đổi mật khẩu
    @PUT("api/users/me/password")
    Call<ApiResponse<Void>> changePassword(@Body Map<String, String> body);

    // Đăng ký / cập nhật FCM token cho push notification
    @PUT("api/users/me/fcm-token")
    Call<ApiResponse<Void>> updateFcmToken(@Body Map<String, String> body);
}
