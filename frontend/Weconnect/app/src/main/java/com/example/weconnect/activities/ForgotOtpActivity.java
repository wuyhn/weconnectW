package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForgotOtpActivity extends AppCompatActivity {

    private EditText[] otpBoxes;
    private TextView tvOtpEmail;
    private TextView tvOtpError;
    private TextView tvResendOtp;
    private TextView tvResendTimer;
    private MaterialButton btnNext;

    private CountDownTimer countDownTimer;
    private AuthApiService authApiService;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_otp);

        email = getIntent().getStringExtra("email");
        if (TextUtils.isEmpty(email)) {
            finish();
            return;
        }

        authApiService = RetrofitClient.getClient().create(AuthApiService.class);

        initViews();
        setupOtpNavigation();
        startCountdown();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelCountdown();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        ivBack.setOnClickListener(v -> finish());

        tvOtpEmail    = findViewById(R.id.tvOtpEmail);
        tvOtpError    = findViewById(R.id.tvOtpError);
        tvResendOtp   = findViewById(R.id.tvResendOtp);
        tvResendTimer = findViewById(R.id.tvResendTimer);
        btnNext       = findViewById(R.id.btnNext);

        otpBoxes = new EditText[]{
                findViewById(R.id.etOtp1),
                findViewById(R.id.etOtp2),
                findViewById(R.id.etOtp3),
                findViewById(R.id.etOtp4),
                findViewById(R.id.etOtp5),
                findViewById(R.id.etOtp6)
        };

        tvOtpEmail.setText(email);
        tvResendOtp.setOnClickListener(v -> handleResend());
        btnNext.setOnClickListener(v -> handleNext());
        updateNextButtonState();
    }

    private void setupOtpNavigation() {
        for (int i = 0; i < otpBoxes.length; i++) {
            final int index = i;

            otpBoxes[i].addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() == 1) {
                        if (index < otpBoxes.length - 1) {
                            otpBoxes[index + 1].requestFocus();
                        } else {
                            hideKeyboard(otpBoxes[index]);
                        }
                    }
                    updateNextButtonState();
                    tvOtpError.setVisibility(View.GONE);
                }
            });

            otpBoxes[i].setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && TextUtils.isEmpty(otpBoxes[index].getText())
                        && index > 0) {
                    otpBoxes[index - 1].setText("");
                    otpBoxes[index - 1].requestFocus();
                }
                return false;
            });
        }
    }

    private void updateNextButtonState() {
        for (EditText box : otpBoxes) {
            if (TextUtils.isEmpty(box.getText())) {
                btnNext.setEnabled(false);
                btnNext.setAlpha(0.5f);
                return;
            }
        }
        btnNext.setEnabled(true);
        btnNext.setAlpha(1.0f);
    }

    private String getOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (EditText box : otpBoxes) sb.append(box.getText().toString());
        return sb.toString();
    }

    private void clearOtpBoxes() {
        for (EditText box : otpBoxes) box.setText("");
        otpBoxes[0].requestFocus();
        updateNextButtonState();
    }

    private void handleNext() {
        String otp = getOtpCode();
        if (otp.length() != 6) {
            showError("Vui lòng nhập đủ mã OTP 6 chữ số");
            return;
        }
        Intent intent = new Intent(this, ResetPasswordActivity.class);
        intent.putExtra("email", email);
        intent.putExtra("otpCode", otp);
        startActivity(intent);
    }

    private void handleResend() {
        tvResendOtp.setEnabled(false);

        authApiService.forgotPassword(email).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                tvResendOtp.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    clearOtpBoxes();
                    tvOtpError.setVisibility(View.GONE);
                    startCountdown();
                    Toast.makeText(ForgotOtpActivity.this,
                            "Mã OTP mới đã gửi đến " + email, Toast.LENGTH_SHORT).show();
                } else {
                    String msg = (response.body() != null && response.body().getMessage() != null)
                            ? response.body().getMessage() : "Gửi lại OTP thất bại.";
                    showError(msg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                tvResendOtp.setEnabled(true);
                showError("Không thể kết nối máy chủ. Vui lòng thử lại.");
            }
        });
    }

    private void startCountdown() {
        cancelCountdown();
        tvResendOtp.setEnabled(false);
        tvResendOtp.setAlpha(0.4f);
        tvResendTimer.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(60_000, 1_000) {
            @Override
            public void onTick(long ms) {
                tvResendTimer.setText("Gửi lại sau " + (ms / 1000) + "s");
            }
            @Override
            public void onFinish() {
                tvResendOtp.setEnabled(true);
                tvResendOtp.setAlpha(1.0f);
                tvResendTimer.setVisibility(View.GONE);
            }
        }.start();
    }

    private void cancelCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void showError(String message) {
        tvOtpError.setText(message);
        tvOtpError.setVisibility(View.VISIBLE);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
