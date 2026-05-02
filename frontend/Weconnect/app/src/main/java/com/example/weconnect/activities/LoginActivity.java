package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvErrorEmail, tvErrorPassword, tvRegister;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupSmartValidation();

        // Nếu đã đăng nhập rồi → vào thẳng MainActivity
        if (FirebaseManager.isLoggedIn()) {
            startMainActivity();
            return;
        }

        // Hiệu ứng nảy cho nút Đăng nhập
        btnLogin.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            }
            return false;
        });

        btnLogin.setOnClickListener(v -> {
            checkFieldsOnSubmit();
            if (validateAllFields()) {
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();
                loginWithFirebase(email, password);
            } else {
                Toast.makeText(this, "Vui lòng hoàn thiện đúng thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        tvRegister.setOnClickListener(v ->
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword.setOnClickListener(v ->
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class))
        );
    }

    // =====================================================================
    // Firebase Auth Login
    // =====================================================================

    private void loginWithFirebase(String email, String password) {
        btnLogin.setEnabled(false);
        btnLogin.setText("Đang đăng nhập...");

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                String uid = authResult.getUser().getUid();
                loadUserProfileAndProceed(uid);
            })
            .addOnFailureListener(e -> {
                btnLogin.setEnabled(true);
                btnLogin.setText("Đăng nhập");

                String msg = e.getMessage();
                if (msg != null && (msg.contains("password") || msg.contains("credential") || msg.contains("INVALID"))) {
                    showError(tvErrorPassword, "Sai email hoặc mật khẩu");
                } else if (msg != null && msg.contains("no user")) {
                    showError(tvErrorEmail, "Tài khoản không tồn tại");
                } else {
                    Toast.makeText(this, "Đăng nhập thất bại: " + msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    /** Sau khi login thành công → đọc users/{uid} để lấy fullName, kiểm tra isBlocked */
    private void loadUserProfileAndProceed(String uid) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener(doc -> {
                if (!doc.exists()) {
                    // Profile chưa tạo → vẫn cho vào app
                    FirebaseManager.saveUserId(this, uid);
                    FirebaseManager.saveUserName(this, "");
                    startMainActivity();
                    return;
                }

                // Kiểm tra tài khoản bị khóa
                Boolean isBlocked = doc.getBoolean("isBlocked");
                if (Boolean.TRUE.equals(isBlocked)) {
                    FirebaseAuth.getInstance().signOut();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Đăng nhập");
                    showError(tvErrorPassword, "Tài khoản của bạn hiện đang bị khóa");
                    return;
                }

                String fullName = doc.getString("fullName");
                FirebaseManager.saveUserId(this, uid);
                FirebaseManager.saveUserName(this, fullName != null ? fullName : "");

                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                startMainActivity();
            })
            .addOnFailureListener(e -> {
                // Lỗi đọc Firestore, nhưng Auth đã oke → vẫn vào app
                FirebaseManager.saveUserId(this, uid);
                startMainActivity();
            });
    }

    private void startMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =====================================================================
    // Validation (giữ nguyên logic cũ)
    // =====================================================================

    private void setupSmartValidation() {
        etEmail.addTextChangedListener(new SimpleTextWatcher(s -> {
            String email = s.toString().trim();
            if (email.isEmpty()) {
                showError(tvErrorEmail, "Email không được để trống");
            } else if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$")) {
                showError(tvErrorEmail, "Email phải kết thúc bằng .com");
            } else {
                tvErrorEmail.setVisibility(View.GONE);
            }
        }));

        etPassword.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(tvErrorPassword, "Mật khẩu không được để trống");
            } else if (s.length() < 8) {
                showError(tvErrorPassword, "Mật khẩu phải ít nhất 8 ký tự");
            } else {
                tvErrorPassword.setVisibility(View.GONE);
            }
        }));
    }

    private void showError(TextView tv, String message) {
        tv.setText("⚠ " + message);
        tv.setVisibility(View.VISIBLE);
    }

    private void checkFieldsOnSubmit() {
        if (etEmail.getText().toString().isEmpty()) showError(tvErrorEmail, "Email không được để trống");
        if (etPassword.getText().toString().isEmpty()) showError(tvErrorPassword, "Mật khẩu không được để trống");
    }

    private boolean validateAllFields() {
        return tvErrorEmail.getVisibility() == View.GONE &&
                tvErrorPassword.getVisibility() == View.GONE &&
                !etEmail.getText().toString().isEmpty() &&
                !etPassword.getText().toString().isEmpty();
    }

    private void initViews() {
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvErrorEmail    = findViewById(R.id.tvErrorEmail);
        tvErrorPassword = findViewById(R.id.tvErrorPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
    }

    interface TextChangedListener { void onTextChanged(CharSequence s); }
    class SimpleTextWatcher implements TextWatcher {
        private final TextChangedListener listener;
        public SimpleTextWatcher(TextChangedListener l) { this.listener = l; }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { listener.onTextChanged(s); }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}