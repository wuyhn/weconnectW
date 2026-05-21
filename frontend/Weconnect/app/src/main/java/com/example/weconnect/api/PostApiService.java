package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.PostResponse;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface PostApiService {

    // Upload image
    @Multipart
    @POST("api/upload/image")
    Call<ApiResponse<String>> uploadImage(@Part MultipartBody.Part file);

    // Danh sách bài đăng active (Home feed)
    @GET("api/posts")
    Call<ApiResponse<List<PostResponse>>> getActivePosts();

    // Chi tiết bài đăng
    @GET("api/posts/{id}")
    Call<ApiResponse<PostResponse>> getPost(@Path("id") long id);

    // Tạo bài đăng mới
    @POST("api/posts")
    Call<ApiResponse<PostResponse>> createPost(@Body Map<String, Object> body);

    // Sửa bài đăng
    @PUT("api/posts/{id}")
    Call<ApiResponse<PostResponse>> updatePost(@Path("id") long id, @Body Map<String, Object> body);

    // Xóa bài đăng
    @DELETE("api/posts/{id}")
    Call<ApiResponse<Void>> deletePost(@Path("id") long id);

    // Hủy hoạt động (chủ bài đăng)
    @POST("api/posts/{id}/cancel")
    Call<ApiResponse<Void>> cancelActivity(@Path("id") long id);

    // Xin tham gia
    @POST("api/posts/{id}/join")
    Call<ApiResponse<Void>> joinPost(@Path("id") long id);

    // Duyệt thành viên
    @POST("api/posts/{id}/approve/{userId}")
    Call<ApiResponse<Void>> approveMember(@Path("id") long id, @Path("userId") long userId);

    // Từ chối thành viên
    @POST("api/posts/{id}/reject/{userId}")
    Call<ApiResponse<Void>> rejectMember(@Path("id") long id, @Path("userId") long userId);

    // Danh sách chờ duyệt
    @GET("api/posts/{id}/pending")
    Call<ApiResponse<List<Map<String, Object>>>> getPendingMembers(@Path("id") long id);

    // Danh sách thành viên
    @GET("api/posts/{id}/members")
    Call<ApiResponse<List<Map<String, Object>>>> getMembers(@Path("id") long id);

    // Tìm kiếm
    @GET("api/posts/search")
    Call<ApiResponse<List<PostResponse>>> searchPosts(@Query("q") String query);

    // Hoạt động của tôi
    @GET("api/posts/my-activities")
    Call<ApiResponse<List<PostResponse>>> getMyActivities();

    // Bài đăng của user
    @GET("api/posts/user/{userId}")
    Call<ApiResponse<List<PostResponse>>> getUserPosts(@Path("userId") long userId);

    // Bài đăng đã lưu trữ / hết hạn của user
    @GET("api/posts/user/{userId}/archived")
    Call<ApiResponse<List<PostResponse>>> getUserArchivedPosts(@Path("userId") long userId);

    // Hoạt động user đã tham gia
    @GET("api/posts/user/{userId}/activities")
    Call<ApiResponse<List<PostResponse>>> getUserActivities(@Path("userId") long userId);
}

