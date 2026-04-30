package com.example.weconnect.notification.data;

import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.notification.data.NotificationItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.*;

public interface NotificationApiService {

    // Lấy danh sách thông báo
    @GET("api/notifications")
    Call<ApiResponse<List<NotificationItem>>> getNotifications();

    // Đánh dấu đã đọc
    @PUT("api/notifications/{id}/read")
    Call<ApiResponse<Void>> markAsRead(@Path("id") long id);

    // Đọc tất cả
    @PUT("api/notifications/read-all")
    Call<ApiResponse<Void>> markAllAsRead();

    // Đánh dấu đã xử lý
    @PUT("api/notifications/{id}/action")
    Call<ApiResponse<Void>> markAsActioned(@Path("id") long id);

    // Số chưa đọc
    @GET("api/notifications/unread-count")
    Call<ApiResponse<Integer>> getUnreadCount();
}
