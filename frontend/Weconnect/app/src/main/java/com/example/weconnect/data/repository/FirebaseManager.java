package com.example.weconnect.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

/**
 * FirebaseManager — singleton thay thế RetrofitClient.
 * Cung cấp Firebase Auth, Firestore, Storage instance.
 * Lưu session (UID, tên) vào SharedPreferences giống RetrofitClient cũ.
 */
public class FirebaseManager {

    private static final String PREFS_NAME = "weconnect_prefs";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_ID   = "user_id";   // Firebase UID (String)

    // === Firebase Instances ===

    public static FirebaseAuth getAuth() {
        return FirebaseAuth.getInstance();
    }

    public static FirebaseFirestore getFirestore() {
        return FirebaseFirestore.getInstance();
    }

    public static FirebaseStorage getStorage() {
        return FirebaseStorage.getInstance();
    }

    // === Current User Helpers ===

    /** Trả về Firebase UID của user đang đăng nhập, hoặc null nếu chưa đăng nhập */
    public static String getCurrentUserId() {
        FirebaseUser user = getAuth().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /** Trả về email của user đang đăng nhập */
    public static String getCurrentUserEmail() {
        FirebaseUser user = getAuth().getCurrentUser();
        return user != null ? user.getEmail() : null;
    }

    /** Kiểm tra user đã đăng nhập chưa */
    public static boolean isLoggedIn() {
        return getAuth().getCurrentUser() != null;
    }

    // === Session Helpers (SharedPreferences) ===

    public static void saveUserName(Context context, String name) {
        getPrefs(context).edit().putString(KEY_USER_NAME, name).apply();
    }

    public static String getUserName(Context context) {
        return getPrefs(context).getString(KEY_USER_NAME, "");
    }

    public static void saveUserId(Context context, String uid) {
        getPrefs(context).edit().putString(KEY_USER_ID, uid).apply();
    }

    public static String getUserId(Context context) {
        // Ưu tiên lấy từ FirebaseAuth để luôn mới nhất
        String uid = getCurrentUserId();
        if (uid != null) return uid;
        return getPrefs(context).getString(KEY_USER_ID, "");
    }

    /** Đăng xuất: xóa Firebase session + SharedPreferences */
    public static void clearSession(Context context) {
        getAuth().signOut();
        getPrefs(context).edit().clear().apply();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
