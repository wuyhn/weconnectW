package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatMessageApiResponse;
import com.example.weconnect.models.ChatRoomApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface ChatApiService {

    // Danh sách phòng chat của user
    @GET("api/chat/rooms")
    Call<ApiResponse<List<ChatRoomApiResponse>>> getRooms();

    // Chi tiết phòng chat
    // Danh sach direct room tu nguoi chua la ban be
    @GET("api/chat/message-requests")
    Call<ApiResponse<List<ChatRoomApiResponse>>> getMessageRequests();

    @GET("api/chat/rooms/{id}")
    Call<ApiResponse<ChatRoomApiResponse>> getRoom(@Path("id") long id);

    // Lấy hoặc tạo phòng DM
    @GET("api/chat/direct/{userId}")
    Call<ApiResponse<ChatRoomApiResponse>> getDirectRoom(@Path("userId") long userId);

    // Tạo phòng nhóm bạn bè
    @POST("api/chat/rooms")
    Call<ApiResponse<ChatRoomApiResponse>> createGroupRoom(@Body Map<String, Object> body);

    // Lịch sử tin nhắn
    @GET("api/chat/rooms/{id}/messages")
    Call<ApiResponse<List<ChatMessageApiResponse>>> getMessages(@Path("id") long id);

    // Tin nhắn mới (polling)
    @GET("api/chat/rooms/{id}/messages/new")
    Call<ApiResponse<List<ChatMessageApiResponse>>> getNewMessages(
            @Path("id") long id, @Query("afterId") long afterId);

    // Gửi tin nhắn
    @POST("api/chat/rooms/{id}/messages")
    Call<ApiResponse<ChatMessageApiResponse>> sendMessage(
            @Path("id") long id, @Body Map<String, String> body);

    // Lấy phòng chat theo postId
    @GET("api/chat/rooms/post/{postId}")
    Call<ApiResponse<ChatRoomApiResponse>> getRoomByPostId(@Path("postId") long postId);

    // Đánh dấu đã đọc tất cả tin nhắn trong phòng
    @PUT("api/chat/rooms/{id}/read")
    Call<ApiResponse<Void>> markRoomAsRead(@Path("id") long id);

    // Hủy hoạt động (owner of activity room only)
    @POST("api/chat/rooms/{id}/cancel")
    Call<ApiResponse<Void>> cancelActivityRoom(@Path("id") long id);

    // Xóa phòng chat (owner only)
    @DELETE("api/chat/rooms/{id}")
    Call<ApiResponse<Void>> deleteRoom(@Path("id") long id);

    // Xóa thành viên khỏi nhóm (owner only)
    @DELETE("api/chat/rooms/{id}/members/{memberId}")
    Call<ApiResponse<Void>> removeMember(@Path("id") long id, @Path("memberId") long memberId);

    // Rời nhóm (non-owner)
    @POST("api/chat/rooms/{id}/leave")
    Call<ApiResponse<Void>> leaveRoom(@Path("id") long id);
}
