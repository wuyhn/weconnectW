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

    // Emulator dùng 10.0.2.2, điện thoại thật dùng IP LAN của máy tính
    private static final String EMULATOR_URL = "http://10.0.2.2:8081/";
    private static final String DEVICE_URL = "http://192.168.1.18:8081/";

    public static String getBaseUrl() {
        // Detect emulator
        boolean isEmulator = android.os.Build.FINGERPRINT.contains("generic")
                || android.os.Build.FINGERPRINT.contains("emulator")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.PRODUCT.contains("sdk")
                || android.os.Build.HARDWARE.contains("goldfish")
                || android.os.Build.HARDWARE.contains("ranchu");
        return isEmulator ? EMULATOR_URL : DEVICE_URL;
    }
    private static Retrofit retrofit = null;
    private static String authToken = null;

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

    // Load token từ SharedPreferences
    public static void loadToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        authToken = prefs.getString("jwt_token", null);
        retrofit = null;
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

    // Logout
    public static void clearSession(Context context) {
        authToken = null;
        retrofit = null;
        SharedPreferences prefs = context.getSharedPreferences("weconnect_prefs", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
