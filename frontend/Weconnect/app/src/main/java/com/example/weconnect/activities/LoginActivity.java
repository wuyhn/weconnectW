package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.AuthResponse;
import com.example.weconnect.models.LoginRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvErrorEmail, tvErrorPassword, tvRegister;
    private Button btnLogin;

    private AuthApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupSmartValidation();

        // Dùng RetrofitClient chung
        apiService = RetrofitClient.getClient().create(AuthApiService.class);

        // Hiệu ứng nảy cho nút Đăng nhập
        btnLogin.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            }
            return false;
        });

        // Xử lý sự kiện bấm nút Đăng nhập
        btnLogin.setOnClickListener(v -> {
            checkFieldsOnSubmit();

            if (validateAllFields()) {
                String email = etEmail.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                loginWithBackend(email, password);
            } else {
                Toast.makeText(this, "Vui lòng hoàn thiện đúng thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        TextView tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvForgotPassword.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class));
        });
    }

    // Gọi API login mới - nhận JWT token
    private void loginWithBackend(String email, String password) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        Log.e("LoginDebug", "Calling API with BASE_URL=" + RetrofitClient.getBaseUrl());

        apiService.login(loginRequest).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call,
                                   Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AuthResponse authResult = response.body().getResult();

                    // Lưu JWT token và thông tin user
                    RetrofitClient.saveToken(LoginActivity.this, authResult.getToken());
                    RetrofitClient.saveUserId(LoginActivity.this, authResult.getId());
                    RetrofitClient.saveUserName(LoginActivity.this, authResult.getFullName());

                    // Reset tất cả fake repos để tránh trộn dữ liệu giữa các tài khoản
                    com.example.weconnect.data.FakePostRepository.resetInstance();
                    com.example.weconnect.data.FakeSocialRepository.resetInstance();
                    com.example.weconnect.data.FakeNotificationRepository.resetInstance();
                    // Set username cho fake repos
                    com.example.weconnect.data.FakePostRepository.getInstance()
                            .setCurrentUsername(authResult.getFullName());
                    com.example.weconnect.data.FakeSocialRepository.getInstance()
                            .setCurrentUsername(authResult.getFullName());

                    Toast.makeText(LoginActivity.this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    // Lỗi từ backend
                    String errorMsg = "Sai email hoặc mật khẩu";
                    if (response.body() != null) {
                        errorMsg = response.body().getMessage();
                    }
                    showError(tvErrorPassword, errorMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                String err = t.getClass().getSimpleName() + ": " + t.getMessage();
                Toast.makeText(LoginActivity.this,
                        "Lỗi: " + err + "\nURL: " + RetrofitClient.getBaseUrl(),
                        Toast.LENGTH_LONG).show();
                Log.e("LoginDebug", err, t);
            }
        });
    }

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
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvErrorEmail = findViewById(R.id.tvErrorEmail);
        tvErrorPassword = findViewById(R.id.tvErrorPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
    }

    interface TextChangedListener { void onTextChanged(CharSequence s); }
    class SimpleTextWatcher implements TextWatcher {
        private TextChangedListener listener;
        public SimpleTextWatcher(TextChangedListener l) { this.listener = l; }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { listener.onTextChanged(s); }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
