package com.example.weconnect.activities;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.AuthResponse;
import com.example.weconnect.models.ResendOtpRequest;
import com.example.weconnect.models.VerifyOtpRequest;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText[] otpBoxes;
    private TextView tvEmail, tvError, tvResendTimer, tvResendLabel, btnResend;
    private MaterialButton btnVerify;

    private String email;
    private CountDownTimer countDownTimer;
    private boolean canResend = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        email = getIntent().getStringExtra("email");

        tvEmail = findViewById(R.id.tvEmail);
        tvError = findViewById(R.id.tvError);
        tvResendTimer = findViewById(R.id.tvResendTimer);
        tvResendLabel = findViewById(R.id.tvResendLabel);
        btnVerify = findViewById(R.id.btnVerify);
        btnResend = findViewById(R.id.btnResend);

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        tvEmail.setText(email);
        btnResend.setPaintFlags(btnResend.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        setupOtpBoxes();

        btnVerify.setOnClickListener(v -> verifyOtp());
        btnResend.setOnClickListener(v -> {
            if (canResend) resendOtp();
        });

        startCooldown(60);
    }

    private void setupOtpBoxes() {
        for (int i = 0; i < 6; i++) {
            final int index = i;

            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    tvError.setVisibility(View.GONE);
                    if (s.length() == 1 && index < 5) {
                        otpBoxes[index + 1].requestFocus();
                    }
                    updateVerifyButton();
                }

                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            });

            otpBoxes[i].setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DEL
                        && index > 0
                        && otpBoxes[index].getText().toString().isEmpty()) {
                    otpBoxes[index - 1].requestFocus();
                    otpBoxes[index - 1].setText("");
                    return true;
                }
                return false;
            });
        }
    }

    private void updateVerifyButton() {
        for (EditText box : otpBoxes) {
            if (box.getText().toString().isEmpty()) {
                btnVerify.setEnabled(false);
                return;
            }
        }
        btnVerify.setEnabled(true);
    }

    private String getOtp() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : otpBoxes) {
            sb.append(box.getText().toString());
        }
        return sb.toString();
    }

    private void verifyOtp() {
        String otp = getOtp();
        if (otp.length() != 6) {
            showError("Vui lòng nhập đủ 6 chữ số");
            return;
        }

        btnVerify.setEnabled(false);
        btnVerify.setText("Đang xác thực...");

        RetrofitClient.getClient().create(AuthApiService.class)
                .verifyOtp(new VerifyOtpRequest(email, otp))
                .enqueue(new Callback<ApiResponse<AuthResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<AuthResponse>> call,
                                           Response<ApiResponse<AuthResponse>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                            AuthResponse auth = resp.body().getResult();
                            if (auth != null) {
                                RetrofitClient.saveTokens(OtpVerificationActivity.this, auth.getToken(), auth.getRefreshToken());
                                RetrofitClient.saveUserId(OtpVerificationActivity.this, auth.getId());
                                RetrofitClient.saveUserName(OtpVerificationActivity.this, auth.getFullName());
                                RetrofitClient.saveReputationScore(OtpVerificationActivity.this, auth.getReputationScore());
                            }
                            Intent intent = new Intent(OtpVerificationActivity.this, OnboardingPersonalInfoActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            String msg = (resp.body() != null) ? resp.body().getMessage() : "Mã OTP không đúng";
                            showError(msg);
                            btnVerify.setEnabled(true);
                            btnVerify.setText("Xác thực");
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                        showError("Lỗi kết nối. Vui lòng thử lại.");
                        btnVerify.setEnabled(true);
                        btnVerify.setText("Xác thực");
                    }
                });
    }

    private void resendOtp() {
        canResend = false;
        btnResend.setTextColor(0xFF8E8E93);

        RetrofitClient.getClient().create(AuthApiService.class)
                .resendOtp(new ResendOtpRequest(email))
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call,
                                           Response<ApiResponse<Void>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                            Toast.makeText(OtpVerificationActivity.this,
                                    "Mã OTP mới đã được gửi đến email của bạn", Toast.LENGTH_SHORT).show();
                            startCooldown(60);
                        } else {
                            String msg = (resp.body() != null) ? resp.body().getMessage()
                                    : "Không thể gửi lại mã OTP";
                            Toast.makeText(OtpVerificationActivity.this, msg, Toast.LENGTH_LONG).show();
                            canResend = true;
                            btnResend.setTextColor(getResources().getColor(R.color.primary_pink, getTheme()));
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        Toast.makeText(OtpVerificationActivity.this,
                                "Lỗi kết nối. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                        canResend = true;
                        btnResend.setTextColor(getResources().getColor(R.color.primary_pink, getTheme()));
                    }
                });
    }

    private void startCooldown(int seconds) {
        canResend = false;
        btnResend.setTextColor(0xFF8E8E93);
        tvResendLabel.setVisibility(View.VISIBLE);
        tvResendTimer.setVisibility(View.VISIBLE);

        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(seconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                tvResendTimer.setText(millisUntilFinished / 1000 + "s");
            }

            @Override
            public void onFinish() {
                canResend = true;
                tvResendLabel.setVisibility(View.GONE);
                tvResendTimer.setVisibility(View.GONE);
                btnResend.setTextColor(getResources().getColor(R.color.primary_pink, getTheme()));
            }
        }.start();
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
