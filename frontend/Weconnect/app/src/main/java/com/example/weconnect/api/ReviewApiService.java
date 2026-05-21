package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface ReviewApiService {

    @GET("api/reviews/user/{userId}")
    Call<ApiResponse<List<Map<String, Object>>>> getReviews(@Path("userId") long userId);

    @POST("api/reviews")
    Call<ApiResponse<Map<String, Object>>> createReview(@Body Map<String, Object> body);

    @PUT("api/reviews/{id}")
    Call<ApiResponse<Map<String, Object>>> updateReview(@Path("id") long id, @Body Map<String, Object> body);

    @DELETE("api/reviews/{id}")
    Call<ApiResponse<Void>> deleteReview(@Path("id") long id);

    @GET("api/reviews/can-review/{userId}")
    Call<ApiResponse<Map<String, Object>>> canReview(@Path("userId") long userId);

    @GET("api/reviews/my-review/{userId}")
    Call<ApiResponse<Map<String, Object>>> getMyReview(@Path("userId") long userId);

    @GET("api/reviews/common-activities/{userId}")
    Call<ApiResponse<List<Map<String, Object>>>> getCommonActivities(@Path("userId") long userId);
}
