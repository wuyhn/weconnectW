package com.example.weconnect.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.google.android.material.button.MaterialButton;

public class ForgotPasswordActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        ImageView ivBack = findViewById(R.id.ivBackForgot);
        EditText etEmail = findViewById(R.id.etForgotEmail);
        TextView tvError = findViewById(R.id.tvForgotError);
        MaterialButton btnSend = findViewById(R.id.btnSendReset);
        LinearLayout layoutSuccess = findViewById(R.id.layoutSuccess);
        MaterialButton btnBackToLogin = findViewById(R.id.btnBackToLogin);

        ivBack.setOnClickListener(v -> finish());

        btnSend.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                tvError.setText("Vui lòng nhập email");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvError.setText("Email không hợp lệ");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            tvError.setVisibility(View.GONE);

            // Simulate sending email
            btnSend.setEnabled(false);
            btnSend.setText("Đang gửi...");

            btnSend.postDelayed(() -> {
                btnSend.setVisibility(View.GONE);
                layoutSuccess.setVisibility(View.VISIBLE);
            }, 1500);
        });

        btnBackToLogin.setOnClickListener(v -> finish());
    }
}
