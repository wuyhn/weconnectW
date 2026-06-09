package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText etEmail;
    private TextView tvEmailError;
    private MaterialButton btnSendOtp;
    private AuthApiService authApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        authApiService = RetrofitClient.getClient().create(AuthApiService.class);

        ImageView ivBack = findViewById(R.id.ivBackForgot);
        ivBack.setOnClickListener(v -> finish());

        etEmail      = findViewById(R.id.etForgotEmail);
        tvEmailError = findViewById(R.id.tvEmailError);
        btnSendOtp   = findViewById(R.id.btnSendOtp);

        btnSendOtp.setOnClickListener(v -> handleSendOtp());
    }

    private void handleSendOtp() {
        String email = etEmail.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            showEmailError("Vui lòng nhập email");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showEmailError("Email không hợp lệ");
            return;
        }

        tvEmailError.setVisibility(View.GONE);
        btnSendOtp.setEnabled(false);
        btnSendOtp.setText("Đang gửi…");

        authApiService.forgotPassword(email).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                btnSendOtp.setEnabled(true);
                btnSendOtp.setText("Gửi mã OTP");

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Intent intent = new Intent(ForgotPasswordActivity.this, ForgotOtpActivity.class);
                    intent.putExtra("email", email);
                    startActivity(intent);
                } else {
                    if (response.code() == 404) {
                        showEmailError("Email này chưa được đăng ký trong hệ thống");
                    } else {
                        String msg = (response.body() != null && response.body().getMessage() != null)
                                ? response.body().getMessage()
                                : "Gửi OTP thất bại. Vui lòng thử lại.";
                        showEmailError(msg);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                btnSendOtp.setEnabled(true);
                btnSendOtp.setText("Gửi mã OTP");
                showEmailError("Không thể kết nối máy chủ. Vui lòng thử lại.");
            }
        });
    }

    private void showEmailError(String message) {
        tvEmailError.setText(message);
        tvEmailError.setVisibility(View.VISIBLE);
    }
}
