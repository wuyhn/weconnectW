package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface ReportApiService {

    // Gửi báo cáo (report user hoặc post)
    @POST("api/reports")
    Call<ApiResponse<Void>> createReport(@Body Map<String, Object> body);
}
