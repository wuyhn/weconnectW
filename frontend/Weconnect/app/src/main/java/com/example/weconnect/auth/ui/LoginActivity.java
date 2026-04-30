package com.example.weconnect.auth.ui;

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
import androidx.lifecycle.ViewModelProvider;
import com.example.weconnect.main.ui.MainActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private TextView tvErrorEmail, tvErrorPassword, tvRegister;
    private Button btnLogin;
    private LoginViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setupSmartValidation();

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        setupObservers();

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

                viewModel.login(email, password);
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

    private void setupObservers() {
        viewModel.getIsLoading().observe(this, isLoading -> {
            btnLogin.setEnabled(!isLoading);
            btnLogin.setText(isLoading ? "Đang xử lý..." : "Đăng nhập");
        });

        viewModel.getErrorMessage().observe(this, errorMsg -> {
            if (errorMsg != null) {
                showError(tvErrorPassword, errorMsg);
            }
        });

        viewModel.getLoginSuccess().observe(this, authResult -> {
            if (authResult != null) {
                // Lưu JWT token và thông tin user
                com.example.weconnect.core.RetrofitClient.saveToken(this, authResult.getToken());
                com.example.weconnect.core.RetrofitClient.saveUserId(this, authResult.getId());
                com.example.weconnect.core.RetrofitClient.saveUserName(this, authResult.getFullName());

                // Reset tất cả fake repos để tránh trộn dữ liệu giữa các tài khoản
                com.example.weconnect.social.FakeSocialRepository.resetInstance();
                com.example.weconnect.post.FakePostRepository.resetInstance();
                com.example.weconnect.notification.FakeNotificationRepository.resetInstance();
                
                // Set username cho fake repos
                com.example.weconnect.post.FakePostRepository.getInstance()
                        .setCurrentUsername(authResult.getFullName());
                com.example.weconnect.social.FakeSocialRepository.getInstance()
                        .setCurrentUsername(authResult.getFullName());

                Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
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