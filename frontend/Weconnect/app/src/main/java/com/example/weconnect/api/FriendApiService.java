package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface FriendApiService {

    // Gửi lời mời kết bạn
    @POST("api/friends/request/{userId}")
    Call<ApiResponse<Void>> sendFriendRequest(@Path("userId") long userId);

    // Chấp nhận lời mời
    @POST("api/friends/accept/{userId}")
    Call<ApiResponse<Void>> acceptFriend(@Path("userId") long userId);

    // Từ chối lời mời
    @POST("api/friends/decline/{userId}")
    Call<ApiResponse<Void>> declineFriend(@Path("userId") long userId);

    // Hủy lời mời đã gửi
    @POST("api/friends/cancel/{userId}")
    Call<ApiResponse<Void>> cancelFriend(@Path("userId") long userId);

    // Hủy kết bạn
    @DELETE("api/friends/{userId}")
    Call<ApiResponse<Void>> unfriend(@Path("userId") long userId);

    // Danh sách bạn bè
    @GET("api/friends")
    Call<ApiResponse<List<Map<String, Object>>>> getFriends();

    // Trạng thái quan hệ với user khác
    @GET("api/friends/status/{userId}")
    Call<ApiResponse<String>> getFriendStatus(@Path("userId") long userId);

    // Số bạn bè
    @GET("api/friends/count")
    Call<ApiResponse<Integer>> getFriendCount();

    // Chặn user
    @POST("api/friends/block/{userId}")
    Call<ApiResponse<Void>> blockUser(@Path("userId") long userId);

    // Bỏ chặn
    @DELETE("api/friends/block/{userId}")
    Call<ApiResponse<Void>> unblockUser(@Path("userId") long userId);

    // Danh sách đã chặn
    @GET("api/friends/blocked")
    Call<ApiResponse<List<Map<String, Object>>>> getBlockedUsers();
}
