package com.example.weconnect.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static final String BASE_URL = "http://192.168.1.18:8080/";
    // Dien thoai that
//    private static final String BASE_URL = "http://10.0.2.2:8080/";       // Emulator
    public static String getBaseUrl() {
        return BASE_URL;
    }

    private static Retrofit retrofit = null;
    private static String authToken = null;
    private static String refreshToken = null;
    private static Context appContext = null;
    private static final Object refreshLock = new Object();
    private static String currentAvatarUrl = null;
    private static double currentReputationScore = 60.0;
    // Global cache: userId → avatarUrl cho tất cả user (nhận qua WebSocket realtime)
    private static final java.util.Map<Long, String> userAvatarCache = new java.util.HashMap<>();

    // Callback để MainActivity/LoginActivity xử lý khi token bị từ chối do tài khoản bị khóa
    public interface AccountLockedListener {
        void onAccountLocked();
    }
    private static AccountLockedListener accountLockedListener = null;
    public static void setAccountLockedListener(AccountLockedListener listener) {
        accountLockedListener = listener;
    }

    private static void rememberContext(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    public static Retrofit getClient() {
        if (retrofit == null) {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request.Builder requestBuilder = chain.request().newBuilder();
                        if (authToken != null && chain.request().header("Authorization") == null) {
                            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
                        }
                        okhttp3.Response response = chain.proceed(requestBuilder.build());
                        // 401 với body ACCOUNT_LOCKED → tài khoản bị khóa mid-session
                        if (response.code() == 401 && accountLockedListener != null) {
                            okhttp3.ResponseBody body = response.peekBody(256);
                            if (body != null && body.string().contains("ACCOUNT_LOCKED")) {
                                accountLockedListener.onAccountLocked();
                            }
                        }
                        String requestPath = chain.request().url().encodedPath();
                        if (response.code() == 401
                                && !requestPath.contains("/api/auth/")
                                && chain.request().header("X-Token-Retry") == null
                                && refreshAccessTokenBlocking()) {
                            response.close();
                            Request retryRequest = chain.request().newBuilder()
                                    .removeHeader("Authorization")
                                    .addHeader("Authorization", "Bearer " + authToken)
                                    .addHeader("X-Token-Retry", "true")
                                    .build();
                            return chain.proceed(retryRequest);
                        }
                        return response;
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

    public static boolean hasValidToken(Context context) {
        rememberContext(context);
        loadToken(context);
        if (authToken != null && !authToken.isEmpty() && !isTokenExpired(authToken)) {
            return true;
        }
        if (refreshToken != null && !refreshToken.isEmpty() && !isTokenExpired(refreshToken) && refreshAccessTokenBlocking()) {
            return true;
        }
        clearSession(context);
        return false;
    }

    public static boolean isAuthTokenExpired() {
        return isTokenExpired(authToken);
    }

    private static boolean isTokenExpired(String token) {
        if (token == null || token.isEmpty()) {
            return true;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return true;
            }

            byte[] decodedPayload = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING
            );
            String payload = new String(decodedPayload, StandardCharsets.UTF_8);
            long expSeconds = new JSONObject(payload).optLong("exp", 0L);
            return expSeconds <= 0L || expSeconds * 1000L <= System.currentTimeMillis();
        } catch (Exception e) {
            return true;
        }
    }

    // Lưu token vào SharedPreferences
    private static boolean refreshAccessTokenBlocking() {
        synchronized (refreshLock) {
            try {
                if (appContext != null) {
                    SharedPreferences prefs = appContext.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
                    refreshToken = prefs.getString("refresh_token", refreshToken);
                }

                if (refreshToken == null || refreshToken.isEmpty() || isTokenExpired(refreshToken)) {
                    return false;
                }

                JSONObject payload = new JSONObject();
                payload.put("refreshToken", refreshToken);

                RequestBody body = RequestBody.create(
                        MediaType.parse("application/json; charset=utf-8"),
                        payload.toString()
                );
                Request request = new Request.Builder()
                        .url(getBaseUrl() + "api/auth/refresh")
                        .post(body)
                        .build();

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseText = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        if (response.code() == 423 && accountLockedListener != null) {
                            accountLockedListener.onAccountLocked();
                        }
                        return false;
                    }

                    JSONObject envelope = new JSONObject(responseText);
                    JSONObject result = envelope.optJSONObject("result");
                    if (result == null) {
                        return false;
                    }

                    String newToken = result.optString("token", null);
                    String newRefreshToken = result.optString("refreshToken", refreshToken);
                    if (newToken == null || newToken.isEmpty()) {
                        return false;
                    }

                    authToken = newToken;
                    if (newRefreshToken != null && !newRefreshToken.isEmpty()) {
                        refreshToken = newRefreshToken;
                    }

                    if (appContext != null) {
                        SharedPreferences prefs = appContext.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
                        prefs.edit()
                                .putString("jwt_token", authToken)
                                .putString("refresh_token", refreshToken != null ? refreshToken : "")
                                .apply();
                    }

                    retrofit = null;
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static void saveToken(Context context, String token) {
        saveTokens(context, token, refreshToken);
    }

    public static void saveTokens(Context context, String token, String newRefreshToken) {
        rememberContext(context);
        authToken = token;
        refreshToken = newRefreshToken;
        retrofit = null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("jwt_token", token != null ? token : "")
                .putString("refresh_token", newRefreshToken != null ? newRefreshToken : "")
                .apply();
    }

    // Load token từ SharedPreferences.
    // KHÔNG reset retrofit = null ở đây: interceptor đọc authToken dynamically nên
    // OkHttp client được tái sử dụng, giữ connection pool, tránh tạo mới mỗi button click.
    public static void loadToken(Context context) {
        rememberContext(context);
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        authToken = prefs.getString("jwt_token", null);
        refreshToken = prefs.getString("refresh_token", null);
        // retrofit = null  ← ĐÃ XÓA: gây rebuild OkHttp liên tục, chậm network
    }

    // Lưu user ID
    public static String getRefreshToken() {
        return refreshToken;
    }

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

    // Lưu tỉnh/thành phố của user hiện tại (dùng để kiểm tra địa điểm khi đăng bài)
    public static void saveUserProvince(Context context, String provinceId, String provinceName) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putString("user_province_id", provinceId != null ? provinceId : "")
                .putString("user_city", provinceName != null ? provinceName : "")
                .apply();
    }

    public static void saveUserCity(Context context, String city) {
        saveUserProvince(context, getUserProvinceId(context), city);
    }

    public static String getUserCity(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_city", "");
    }

    public static String getUserProvinceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        return prefs.getString("user_province_id", "");
    }

    // Lưu avatar URL của user hiện tại.
    // Luôn lưu dạng relative path (/uploads/...) để tránh stale khi IP thay đổi.
    public static void saveAvatarUrl(Context context, String url) {
        String relative = toRelativePath(url);
        currentAvatarUrl = (relative != null && !relative.isEmpty()) ? relative : null;
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("user_avatar_url", relative != null ? relative : "").apply();
        }
    }

    public static String getAvatarUrl(Context context) {
        if (currentAvatarUrl != null) return currentAvatarUrl;
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        String saved = prefs.getString("user_avatar_url", "");
        // Migrate stale full URLs đã lưu từ trước
        String relative = toRelativePath(saved.isEmpty() ? null : saved);
        currentAvatarUrl = (relative == null || relative.isEmpty()) ? null : relative;
        return currentAvatarUrl;
    }

    // Trích xuất relative path từ URL bất kỳ (full URL hay relative đều ok)
    private static String toRelativePath(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("/")) return url;
        if (url.startsWith("uploads/")) return "/" + url;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                String path = new java.net.URL(url).getPath();
                if (path != null && !path.isEmpty()) return path;
            } catch (Exception ignored) {}
        }
        return url;
    }

    // Cache avatar cho user bất kỳ (dùng sau khi nhận WebSocket avatar-update event)
    // Lưu dạng relative path để adapter có thể build URL với BASE_URL hiện tại
    public static void cacheAvatarForUser(long userId, String url) {
        String relative = toRelativePath(url);
        if (relative != null && !relative.isEmpty()) userAvatarCache.put(userId, relative);
    }

    public static String getCachedAvatarForUser(long userId) {
        return userAvatarCache.get(userId);
    }

    // Logout
    public static void clearSession(Context context) {
        authToken = null;
        refreshToken = null;
        retrofit = null;
        currentAvatarUrl = null;
        currentReputationScore = 60.0;
        userAvatarCache.clear();
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
