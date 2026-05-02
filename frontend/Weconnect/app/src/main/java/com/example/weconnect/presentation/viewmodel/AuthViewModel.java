package com.example.weconnect.presentation.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthViewModel extends AndroidViewModel {

    public enum AuthState {
        LOADING, SUCCESS, ERROR_EMAIL, ERROR_PASSWORD, ERROR_GENERAL
    }

    private final MutableLiveData<AuthState> _authState = new MutableLiveData<>();
    public LiveData<AuthState> authState = _authState;

    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    public LiveData<String> errorMessage = _errorMessage;

    public AuthViewModel(@NonNull Application application) {
        super(application);
    }

    public void login(String email, String password) {
        _authState.setValue(AuthState.LOADING);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                String uid = authResult.getUser().getUid();
                loadUserProfileAndProceed(uid);
            })
            .addOnFailureListener(e -> {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("password") || msg.contains("credential") || msg.contains("INVALID"))) {
                    _errorMessage.setValue("Sai email hoặc mật khẩu");
                    _authState.setValue(AuthState.ERROR_PASSWORD);
                } else if (msg != null && msg.contains("no user")) {
                    _errorMessage.setValue("Tài khoản không tồn tại");
                    _authState.setValue(AuthState.ERROR_EMAIL);
                } else {
                    _errorMessage.setValue("Đăng nhập thất bại: " + msg);
                    _authState.setValue(AuthState.ERROR_GENERAL);
                }
            });
    }

    private void loadUserProfileAndProceed(String uid) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    FirebaseManager.saveUserId(getApplication(), uid);
                    FirebaseManager.saveUserName(getApplication(), "");
                    _authState.setValue(AuthState.SUCCESS);
                    return;
                }

                Boolean isBlocked = doc.getBoolean("isBlocked");
                if (Boolean.TRUE.equals(isBlocked)) {
                    FirebaseAuth.getInstance().signOut();
                    _errorMessage.setValue("Tài khoản của bạn hiện đang bị khóa");
                    _authState.setValue(AuthState.ERROR_PASSWORD);
                    return;
                }

                String fullName = doc.getString("fullName");
                FirebaseManager.saveUserId(getApplication(), uid);
                FirebaseManager.saveUserName(getApplication(), fullName != null ? fullName : "");

                _authState.setValue(AuthState.SUCCESS);
            })
            .addOnFailureListener(e -> {
                FirebaseManager.saveUserId(getApplication(), uid);
                _authState.setValue(AuthState.SUCCESS);
            });
    }

    public void register(String email, String password, String fullName, String birthday, String gender) {
        _authState.setValue(AuthState.LOADING);
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                String uid = authResult.getUser().getUid();
                createFirestoreUserProfile(uid, email, fullName, birthday, gender);
            })
            .addOnFailureListener(e -> {
                String msg = e.getMessage();
                if (msg != null && msg.contains("email address is already in use")) {
                    _errorMessage.setValue("Email này đã được sử dụng");
                    _authState.setValue(AuthState.ERROR_EMAIL);
                } else if (msg != null && msg.contains("badly formatted")) {
                    _errorMessage.setValue("Email không đúng định dạng");
                    _authState.setValue(AuthState.ERROR_EMAIL);
                } else if (msg != null && msg.contains("weak-password")) {
                    _errorMessage.setValue("Mật khẩu quá yếu (tối thiểu 8 ký tự)");
                    _authState.setValue(AuthState.ERROR_PASSWORD);
                } else {
                    _errorMessage.setValue("Đăng ký thất bại: " + msg);
                    _authState.setValue(AuthState.ERROR_GENERAL);
                }
            });
    }

    private void createFirestoreUserProfile(String uid, String email, String fullName, String birthday, String gender) {
        java.util.Map<String, Object> userDoc = new java.util.HashMap<>();
        userDoc.put("email", email);
        userDoc.put("fullName", fullName);
        userDoc.put("birthday", birthday);
        userDoc.put("gender", gender);
        userDoc.put("avatarUrl", "");
        userDoc.put("bio", "");
        userDoc.put("interestTags", "");
        userDoc.put("averageRating", 0.0f);
        userDoc.put("reputationScore", 0);
        userDoc.put("isBlocked", false);
        userDoc.put("role", 0);
        userDoc.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(userDoc)
            .addOnSuccessListener(aVoid -> {
                FirebaseManager.saveUserId(getApplication(), uid);
                FirebaseManager.saveUserName(getApplication(), fullName);
                _authState.setValue(AuthState.SUCCESS);
            })
            .addOnFailureListener(e -> {
                FirebaseManager.saveUserId(getApplication(), uid);
                FirebaseManager.saveUserName(getApplication(), fullName);
                _authState.setValue(AuthState.SUCCESS);
            });
    }

    public void resetPassword(String email) {
        _authState.setValue(AuthState.LOADING);
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnSuccessListener(unused -> _authState.setValue(AuthState.SUCCESS))
            .addOnFailureListener(e -> {
                String msg = e.getMessage();
                if (msg != null && msg.contains("no user record")) {
                    _errorMessage.setValue("Tài khoản không tồn tại");
                    _authState.setValue(AuthState.ERROR_EMAIL);
                } else {
                    _errorMessage.setValue("Gửi email thất bại: " + msg);
                    _authState.setValue(AuthState.ERROR_GENERAL);
                }
            });
    }
}
