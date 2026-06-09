package com.example.weconnect.api;

import com.example.weconnect.models.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface ReportApiService {

    // Gửi báo cáo (report user hoặc post)
    @POST("api/reports")
    Call<ApiResponse<Void>> createReport(@Body Map<String, Object> body);

    // Lấy chi tiết báo cáo mà user bị xử lý
    @GET("api/reports/{id}/my-detail")
    Call<ApiResponse<Map<String, Object>>> getMyReportDetail(@Path("id") long reportId);

    // Lấy kết quả xử lý báo cáo cho người đã gửi báo cáo
    @GET("api/reports/{id}/reporter-detail")
    Call<ApiResponse<Map<String, Object>>> getReporterReportDetail(@Path("id") long reportId);
}
