package com.example.weconnect.api;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

//    private static final String BASE_URL = "http://192.168.10.234:8080/"; // Điện thoại thật
    private static final String BASE_URL = "http://10.0.2.2:8080/";       // Emulator
    public static String getBaseUrl() {
        return BASE_URL;
    }

    private static Retrofit retrofit = null;
    private static String authToken = null;
    private static String currentAvatarUrl = null;
    private static double currentReputationScore = 60.0;
    // Global cache: userId → avatarUrl cho tất cả user (nhận qua WebSocket realtime)
    private static final java.util.Map<Long, String> userAvatarCache = new java.util.HashMap<>();

    public static Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request.Builder requestBuilder = chain.request().newBuilder();
                        if (authToken != null) {
                            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
                        }
                        return chain.proceed(requestBuilder.build());
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(getBaseUrl())
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static void setAuthToken(String token) {
        authToken = token;
        // Reset retrofit để tạo lại với token mới
        retrofit = null;
    }

    public static String getAuthToken() {
        return authToken;
    }

    // Lưu token vào SharedPreferences
    public static void saveToken(Context context, String token) {
        authToken = token;
        retrofit = null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("jwt_token", token).apply();
    }

    // Load token từ SharedPreferences.
    // KHÔNG reset retrofit = null ở đây: interceptor đọc authToken dynamically nên
    // OkHttp client được tái sử dụng, giữ connection pool, tránh tạo mới mỗi button click.
    public static void loadToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        authToken = prefs.getString("jwt_token", null);
        // retrofit = null  ← ĐÃ XÓA: gây rebuild OkHttp liên tục, chậm network
    }

    // Lưu user ID
    public static void saveUserId(Context context, long userId) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().putLong("user_id", userId).apply();
    }

    public static long getUserId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        return prefs.getLong("user_id", -1);
    }

    // Lưu user name
    public static void saveUserName(Context context, String name) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("user_name", name).apply();
    }

    public static String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_name", "");
    }

    // Luu diem uy tin hien tai de profile khong hien 0 truoc khi API tai xong.
    public static void saveReputationScore(Context context, double score) {
        currentReputationScore = score;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().putFloat("reputation_score", (float) score).apply();
    }

    public static double getReputationScore(Context context) {
        if (context == null) return currentReputationScore;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        currentReputationScore = prefs.getFloat("reputation_score", 60.0f);
        return currentReputationScore;
    }

    // Lưu avatar URL của user hiện tại
    public static void saveAvatarUrl(Context context, String url) {
        currentAvatarUrl = (url != null && !url.isEmpty()) ? url : null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("user_avatar_url", url != null ? url : "").apply();
    }

    public static String getAvatarUrl(Context context) {
        if (currentAvatarUrl != null) return currentAvatarUrl;
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        String saved = prefs.getString("user_avatar_url", "");
        currentAvatarUrl = saved.isEmpty() ? null : saved;
        return currentAvatarUrl;
    }

    // Cache avatar cho user bất kỳ (dùng sau khi nhận WebSocket avatar-update event)
    public static void cacheAvatarForUser(long userId, String url) {
        if (url != null && !url.isEmpty()) userAvatarCache.put(userId, url);
    }

    public static String getCachedAvatarForUser(long userId) {
        return userAvatarCache.get(userId);
    }

    // Logout
    public static void clearSession(Context context) {
        authToken = null;
        retrofit = null;
        currentAvatarUrl = null;
        currentReputationScore = 60.0;
        userAvatarCache.clear();
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
