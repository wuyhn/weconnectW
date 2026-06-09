package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private static final String PASSWORD_REGEX = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$";
    private static final String PASSWORD_RULE_MESSAGE =
            "Mật khẩu phải gồm: Phải chứa ít nhất 8 ký tự, bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt!";

    private TextInputEditText etEmail, etPassword, etConfirmPassword;
    private TextView tvErrorEmail, tvErrorPassword, tvErrorConfirmPassword;
    private MaterialButton btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvErrorEmail = findViewById(R.id.tvErrorEmail);
        tvErrorPassword = findViewById(R.id.tvErrorPassword);
        tvErrorConfirmPassword = findViewById(R.id.tvErrorConfirmPassword);
        btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.tvBackToLogin).setOnClickListener(v -> finish());

        setupValidation();

        btnContinue.setOnClickListener(v -> {
            if (validateAll()) register();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resetRegisterButtonState();
    }

    private void register() {
        btnContinue.setEnabled(false);
        btnContinue.setText("Đang xử lý...");

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        User user = new User();
        user.setEmail(email);
        user.setPassword(password);

        RetrofitClient.getClient().create(AuthApiService.class)
                .register(user).enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                            Intent intent = new Intent(RegisterActivity.this, OtpVerificationActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                        } else {
                            resetRegisterButtonState();
                            String msg = getRegisterErrorMessage(resp);
                            show(tvErrorPassword, msg);
                            Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                        resetRegisterButtonState();
                        Toast.makeText(RegisterActivity.this, "Lỗi kết nối. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void resetRegisterButtonState() {
        btnContinue.setEnabled(true);
        btnContinue.setText("Đăng ký");
        // Nếu màn hình bổ sung ProgressBar hoặc LinearProgressIndicator cho luồng đăng ký,
        // hãy ẩn indicator tại đây để trạng thái loading không bị giữ lại khi quay về từ OTP.
        // progressBar.setVisibility(View.GONE);
    }

    private void setupValidation() {
        etEmail.addTextChangedListener(new Watcher(s -> {
            String v = s.toString().trim();
            if (v.isEmpty()) show(tvErrorEmail, "Email không được để trống");
            else if (!v.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"))
                show(tvErrorEmail, "Email chưa đúng định dạng");
            else hide(tvErrorEmail);
        }));

        etPassword.addTextChangedListener(new Watcher(s -> {
            if (s.length() == 0) show(tvErrorPassword, "Mật khẩu không được để trống");
            else if (!isPasswordValid(s.toString())) show(tvErrorPassword, PASSWORD_RULE_MESSAGE);
            else hide(tvErrorPassword);
            String confirm = etConfirmPassword.getText().toString();
            if (!confirm.isEmpty() && !confirm.equals(s.toString()))
                show(tvErrorConfirmPassword, "Mật khẩu không khớp");
            else if (!confirm.isEmpty()) hide(tvErrorConfirmPassword);
        }));

        etConfirmPassword.addTextChangedListener(new Watcher(s -> {
            String pass = etPassword.getText().toString();
            if (s.length() == 0) show(tvErrorConfirmPassword, "Vui lòng xác nhận mật khẩu");
            else if (!s.toString().equals(pass)) show(tvErrorConfirmPassword, "Mật khẩu không khớp");
            else hide(tvErrorConfirmPassword);
        }));
    }

    private boolean validateAll() {
        String email = etEmail.getText().toString().trim();
        String pass = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        boolean ok = true;
        if (email.isEmpty()) { show(tvErrorEmail, "Email không được để trống"); ok = false; }
        else if (!email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            show(tvErrorEmail, "Email chưa đúng định dạng"); ok = false;
        }
        if (pass.isEmpty()) { show(tvErrorPassword, "Mật khẩu không được để trống"); ok = false; }
        else if (!isPasswordValid(pass)) { show(tvErrorPassword, PASSWORD_RULE_MESSAGE); ok = false; }
        if (confirm.isEmpty()) { show(tvErrorConfirmPassword, "Vui lòng xác nhận mật khẩu"); ok = false; }
        else if (!confirm.equals(pass)) { show(tvErrorConfirmPassword, "Mật khẩu không khớp"); ok = false; }
        return ok;
    }

    private boolean isPasswordValid(String password) {
        return password != null && password.matches(PASSWORD_REGEX);
    }

    private String getRegisterErrorMessage(Response<ApiResponse<Void>> resp) {
        if (resp.body() != null && resp.body().getMessage() != null && !resp.body().getMessage().trim().isEmpty()) {
            return resp.body().getMessage();
        }

        try {
            if (resp.errorBody() != null) {
                JSONObject json = new JSONObject(resp.errorBody().string());
                String message = json.optString("message", "").trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (Exception ignored) {
            // Nếu response lỗi không phải JSON chuẩn, dùng thông báo mặc định cho register.
        }

        return "Đăng ký thất bại";
    }

    private void show(TextView tv, String msg) { tv.setText(msg); tv.setVisibility(View.VISIBLE); }
    private void hide(TextView tv) { tv.setVisibility(View.GONE); }

    interface OnChanged { void changed(CharSequence s); }
    static class Watcher implements TextWatcher {
        private final OnChanged l;
        Watcher(OnChanged l) { this.l = l; }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { l.changed(s); }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
