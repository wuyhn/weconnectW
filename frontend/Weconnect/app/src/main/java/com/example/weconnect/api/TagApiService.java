package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Retrofit interface giao tiếp với TagController trên backend.
 *
 * Mục đích: cung cấp danh sách 60 tag chính thức của hệ thống cho màn hình
 * tạo bài viết và các màn hình khác cần hiển thị toàn bộ tag.
 *
 * Khác với UserApiService.getInterests() (trả về ≤5 tag cá nhân của user),
 * endpoint này luôn trả về đầy đủ 60 tag bất kể user đã chọn sở thích gì.
 */
public interface TagApiService {

    /**
     * Lấy toàn bộ danh sách tag sở thích chính thức của hệ thống.
     *
     * Endpoint: GET /api/tags/all
     * Auth: JWT Bearer token (đã được RetrofitClient đính kèm tự động)
     *
     * Mỗi phần tử trong List<String> là full string "[emoji] [Tên tag]",
     * ví dụ: "⚽ Đá bóng sân cỏ", "🎬 Xem phim rạp"...
     * Dùng InterestTextUtils.stripLeadingIcon() nếu chỉ cần hiển thị phần text.
     */
    @GET("api/tags/all")
    Call<ApiResponse<List<String>>> getAllSystemTags();
}
