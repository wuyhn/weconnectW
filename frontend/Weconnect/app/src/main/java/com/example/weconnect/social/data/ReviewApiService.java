package com.example.weconnect.social.data;

import com.example.weconnect.core.data.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface ReviewApiService {

    // Lấy danh sách đánh giá của user
    @GET("api/reviews/user/{userId}")
    Call<ApiResponse<List<Map<String, Object>>>> getReviews(@Path("userId") long userId);

    // Gửi đánh giá
    @POST("api/reviews")
    Call<ApiResponse<Void>> createReview(@Body Map<String, Object> body);

    // Lấy hoạt động chung giữa mình và user khác
    @GET("api/reviews/common-activities/{userId}")
    Call<ApiResponse<List<Map<String, Object>>>> getCommonActivities(@Path("userId") long userId);
}
