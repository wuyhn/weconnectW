package com.example.weconnect.presentation.ui;
import com.example.weconnect.presentation.ui.*;

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
import com.example.weconnect.data.repository.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.lifecycle.ViewModelProvider;
import com.example.weconnect.databinding.ActivityLoginBinding;
import com.example.weconnect.presentation.viewmodel.AuthViewModel;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupSmartValidation();
        observeViewModel();

        // Nếu đã đăng nhập rồi → vào thẳng MainActivity
        if (FirebaseManager.isLoggedIn()) {
            startMainActivity();
            return;
        }

        // Hiệu ứng nảy cho nút Đăng nhập
        binding.btnLogin.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
            }
            return false;
        });

        binding.btnLogin.setOnClickListener(v -> {
            checkFieldsOnSubmit();
            if (validateAllFields()) {
                String email = binding.etEmail.getText().toString().trim();
                String password = binding.etPassword.getText().toString().trim();
                authViewModel.login(email, password);
            } else {
                Toast.makeText(this, "Vui lòng hoàn thiện đúng thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        binding.tvRegister.setOnClickListener(v ->
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class))
        );

        binding.tvForgotPassword.setOnClickListener(v ->
            startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class))
        );
    }

    private void observeViewModel() {
        authViewModel.authState.observe(this, state -> {
            switch (state) {
                case LOADING:
                    binding.btnLogin.setEnabled(false);
                    binding.btnLogin.setText("Đang đăng nhập...");
                    break;
                case SUCCESS:
                    Toast.makeText(this, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show();
                    startMainActivity();
                    break;
                case ERROR_EMAIL:
                case ERROR_PASSWORD:
                case ERROR_GENERAL:
                    binding.btnLogin.setEnabled(true);
                    binding.btnLogin.setText("Đăng nhập");
                    break;
            }
        });

        authViewModel.errorMessage.observe(this, msg -> {
            AuthViewModel.AuthState state = authViewModel.authState.getValue();
            if (state == AuthViewModel.AuthState.ERROR_EMAIL) {
                showError(binding.tvErrorEmail, msg);
            } else if (state == AuthViewModel.AuthState.ERROR_PASSWORD) {
                showError(binding.tvErrorPassword, msg);
            } else if (state == AuthViewModel.AuthState.ERROR_GENERAL) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupSmartValidation() {
        binding.etEmail.addTextChangedListener(new SimpleTextWatcher(s -> {
            String email = s.toString().trim();
            if (email.isEmpty()) {
                showError(binding.tvErrorEmail, "Email không được để trống");
            } else if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$")) {
                showError(binding.tvErrorEmail, "Email phải kết thúc bằng .com");
            } else {
                binding.tvErrorEmail.setVisibility(View.GONE);
            }
        }));

        binding.etPassword.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(binding.tvErrorPassword, "Mật khẩu không được để trống");
            } else if (s.length() < 8) {
                showError(binding.tvErrorPassword, "Mật khẩu phải ít nhất 8 ký tự");
            } else {
                binding.tvErrorPassword.setVisibility(View.GONE);
            }
        }));
    }

    private void showError(TextView tv, String message) {
        tv.setText("⚠ " + message);
        tv.setVisibility(View.VISIBLE);
    }

    private void checkFieldsOnSubmit() {
        if (binding.etEmail.getText().toString().isEmpty()) showError(binding.tvErrorEmail, "Email không được để trống");
        if (binding.etPassword.getText().toString().isEmpty()) showError(binding.tvErrorPassword, "Mật khẩu không được để trống");
    }

    private boolean validateAllFields() {
        return binding.tvErrorEmail.getVisibility() == View.GONE &&
                binding.tvErrorPassword.getVisibility() == View.GONE &&
                !binding.etEmail.getText().toString().isEmpty() &&
                !binding.etPassword.getText().toString().isEmpty();
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