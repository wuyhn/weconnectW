package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ResetPasswordRequest;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private TextView tvPasswordError;
    private MaterialButton btnResetPassword;

    private AuthApiService authApiService;
    private String email;
    private String otpCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        email   = getIntent().getStringExtra("email");
        otpCode = getIntent().getStringExtra("otpCode");

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(otpCode)) {
            finish();
            return;
        }

        authApiService = RetrofitClient.getClient().create(AuthApiService.class);

        ImageView ivBack = findViewById(R.id.ivBack);
        ivBack.setOnClickListener(v -> finish());

        etNewPassword     = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvPasswordError   = findViewById(R.id.tvPasswordError);
        btnResetPassword  = findViewById(R.id.btnResetPassword);

        btnResetPassword.setOnClickListener(v -> handleResetPassword());
    }

    private void handleResetPassword() {
        String newPass     = etNewPassword.getText() != null
                ? etNewPassword.getText().toString().trim() : "";
        String confirmPass = etConfirmPassword.getText() != null
                ? etConfirmPassword.getText().toString().trim() : "";

        if (TextUtils.isEmpty(newPass)) {
            showPasswordError("Vui lòng nhập mật khẩu mới");
            return;
        }
        if (newPass.length() < 6) {
            showPasswordError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }
        if (!newPass.equals(confirmPass)) {
            showPasswordError("Mật khẩu xác nhận không khớp");
            return;
        }

        tvPasswordError.setVisibility(View.GONE);
        btnResetPassword.setEnabled(false);
        btnResetPassword.setText("Đang xử lý…");

        ResetPasswordRequest request = new ResetPasswordRequest(email, otpCode, newPass);
        authApiService.resetPassword(request).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(ResetPasswordActivity.this,
                            "Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    btnResetPassword.setEnabled(true);
                    btnResetPassword.setText("Đặt lại mật khẩu");

                    String msg = (response.body() != null && response.body().getMessage() != null)
                            ? response.body().getMessage()
                            : "Đặt lại mật khẩu thất bại. Vui lòng thử lại.";

                    if (msg.contains("OTP") || msg.contains("mã") || msg.contains("hết hạn")) {
                        showPasswordError("Mã OTP không hợp lệ hoặc đã hết hạn. Vui lòng quay lại nhập lại mã.");
                    } else {
                        showPasswordError(msg);
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                btnResetPassword.setEnabled(true);
                btnResetPassword.setText("Đặt lại mật khẩu");
                showPasswordError("Không thể kết nối máy chủ. Vui lòng thử lại.");
            }
        });
    }

    private void showPasswordError(String message) {
        tvPasswordError.setText(message);
        tvPasswordError.setVisibility(View.VISIBLE);
    }
}
