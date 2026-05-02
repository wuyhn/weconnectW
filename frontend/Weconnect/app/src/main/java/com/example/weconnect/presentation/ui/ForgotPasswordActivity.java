package com.example.weconnect.presentation.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.weconnect.databinding.ActivityForgotPasswordBinding;
import com.example.weconnect.presentation.viewmodel.AuthViewModel;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ActivityForgotPasswordBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        binding.ivBackForgot.setOnClickListener(v -> finish());
        binding.btnBackToLogin.setOnClickListener(v -> finish());
        
        setupObservers();

        binding.btnSendReset.setOnClickListener(v -> {
            String email = binding.etForgotEmail.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                binding.tvForgotError.setText("Vui lòng nhập email");
                binding.tvForgotError.setVisibility(View.VISIBLE);
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tvForgotError.setText("Email không hợp lệ");
                binding.tvForgotError.setVisibility(View.VISIBLE);
                return;
            }

            binding.tvForgotError.setVisibility(View.GONE);
            authViewModel.resetPassword(email);
        });
    }

    private void setupObservers() {
        authViewModel.authState.observe(this, state -> {
            if (state == AuthViewModel.AuthState.LOADING) {
                binding.btnSendReset.setEnabled(false);
                binding.btnSendReset.setText("Đang gửi...");
            } else if (state == AuthViewModel.AuthState.SUCCESS) {
                binding.btnSendReset.setVisibility(View.GONE);
                binding.layoutSuccess.setVisibility(View.VISIBLE);
            } else {
                binding.btnSendReset.setEnabled(true);
                binding.btnSendReset.setText("Gửi liên kết đặt lại");
            }
        });

        authViewModel.errorMessage.observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                binding.tvForgotError.setText(err);
                binding.tvForgotError.setVisibility(View.VISIBLE);
            }
        });
    }
}
