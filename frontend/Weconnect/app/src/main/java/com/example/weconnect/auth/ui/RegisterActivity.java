package com.example.weconnect.auth.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.weconnect.R;
import com.example.weconnect.auth.data.AuthApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.auth.data.AuthResponse;
import com.example.weconnect.auth.data.LoginRequest;
import com.example.weconnect.profile.data.User;
import java.util.Calendar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etBirthday, etEmail, etPassword;
    private TextView tvErrorName, tvErrorBirthday, tvErrorEmail, tvErrorPassword, tvErrorGender, tvBackToLogin;
    private RadioGroup rgGender;
    private Button btnRegister;

    private AuthApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupSmartValidation();

        // Dùng RetrofitClient chung
        apiService = RetrofitClient.getClient().create(AuthApiService.class);

        btnRegister.setOnClickListener(v -> {
            checkFieldsOnSubmit();

            if (validateAllFields()) {
                registerUser();
            } else {
                Toast.makeText(this, "Vui lòng hoàn thiện đúng thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();

        int selectedId = rgGender.getCheckedRadioButtonId();
        RadioButton radioButton = findViewById(selectedId);
        String gender = radioButton.getText().toString();

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(password);
        newUser.setFullName(fullName);
        newUser.setBirthday(birthday);
        newUser.setGender(gender);

        apiService.register(newUser).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(RegisterActivity.this, "Đăng ký thành công!", Toast.LENGTH_LONG).show();
                    // Lưu tên user để dùng làm nickname trong app
                    RetrofitClient.saveUserName(RegisterActivity.this, fullName);
                    // Tự động đăng nhập để lấy JWT token
                    autoLogin(email, password);
                } else {
                    String error = "Đăng ký thất bại";
                    if (response.body() != null) {
                        error = response.body().getMessage();
                    }
                    Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Log.e("API_ERROR", t.getMessage());
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối Server!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Tự động đăng nhập sau khi đăng ký để lấy JWT token
    private void autoLogin(String email, String password) {
        LoginRequest loginRequest = new LoginRequest(email, password);
        apiService.login(loginRequest).enqueue(new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call,
                                   Response<ApiResponse<AuthResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AuthResponse authResult = response.body().getResult();
                    // Lưu JWT token và thông tin user
                    RetrofitClient.saveToken(RegisterActivity.this, authResult.getToken());
                    RetrofitClient.saveUserId(RegisterActivity.this, authResult.getId());
                    RetrofitClient.saveUserName(RegisterActivity.this, authResult.getFullName());
                }
                // Chuyển sang Onboarding dù login thành công hay không
                goToOnboarding();
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                // Nếu auto-login lỗi, vẫn chuyển sang Onboarding
                goToOnboarding();
            }
        });
    }

    private void goToOnboarding() {
        Intent intent = new Intent(RegisterActivity.this, OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    private void setupSmartValidation() {
        // 1. Kiểm tra Tên
        etFullName.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(tvErrorName, "Họ tên không được để trống");
            } else if (s.length() < 8) {
                showError(tvErrorName, "Họ tên phải ít nhất 8 ký tự");
            } else {
                tvErrorName.setVisibility(View.GONE);
            }
        }));

        // 2. Kiểm tra Ngày sinh (Tự thêm / và check tuổi)
        etBirthday.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().equals(current)) {
                    String clean = s.toString().replaceAll("[^\\d]", "");
                    String formatted = clean;
                    if (clean.length() >= 2 && clean.length() < 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2);
                    else if (clean.length() >= 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2, 4) + "/" + clean.substring(4);

                    current = formatted;
                    etBirthday.setText(current);
                    etBirthday.setSelection(current.length());

                    if (current.isEmpty()) {
                        showError(tvErrorBirthday, "Ngày sinh không được để trống");
                    } else if (current.length() < 10) {
                        showError(tvErrorBirthday, "Vui lòng nhập đủ ngày sinh");
                    } else {
                        if (isValidAge(current)) tvErrorBirthday.setVisibility(View.GONE);
                        else showError(tvErrorBirthday, "Bạn phải từ 18 tuổi trở lên");
                    }
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        // 3. Kiểm tra Email
        etEmail.addTextChangedListener(new SimpleTextWatcher(s -> {
            String email = s.toString().trim();
            if (email.isEmpty()) {
                showError(tvErrorEmail, "Email không được để trống");
            } else if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$")) {
                showError(tvErrorEmail, "Email phải đúng định dạng");
            } else {
                tvErrorEmail.setVisibility(View.GONE);
            }
        }));

        // 4. Kiểm tra Mật khẩu
        etPassword.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(tvErrorPassword, "Mật khẩu không được để trống");
            } else if (s.length() < 8) {
                showError(tvErrorPassword, "Mật khẩu phải ít nhất 8 ký tự");
            } else {
                tvErrorPassword.setVisibility(View.GONE);
            }
        }));

        // 5. Giới tính
        rgGender.setOnCheckedChangeListener((group, checkedId) -> tvErrorGender.setVisibility(View.GONE));
    }

    private void showError(TextView tv, String message) {
        tv.setText("⚠ " + message);
        tv.setVisibility(View.VISIBLE);
    }

    private void checkFieldsOnSubmit() {
        if (etFullName.getText().toString().isEmpty()) showError(tvErrorName, "Họ tên không được để trống");
        if (etBirthday.getText().toString().isEmpty()) showError(tvErrorBirthday, "Ngày sinh không được để trống");
        if (etEmail.getText().toString().isEmpty()) showError(tvErrorEmail, "Email không được để trống");
        if (etPassword.getText().toString().isEmpty()) showError(tvErrorPassword, "Mật khẩu không được để trống");
        if (rgGender.getCheckedRadioButtonId() == -1) showError(tvErrorGender, "Vui lòng chọn giới tính");
    }

    private boolean validateAllFields() {
        // Đảm bảo giới tính đã chọn
        return tvErrorName.getVisibility() == View.GONE &&
                tvErrorBirthday.getVisibility() == View.GONE &&
                tvErrorEmail.getVisibility() == View.GONE &&
                tvErrorPassword.getVisibility() == View.GONE &&
                tvErrorGender.getVisibility() == View.GONE &&
                rgGender.getCheckedRadioButtonId() != -1 &&
                !etEmail.getText().toString().isEmpty();
    }

    private boolean isValidAge(String date) {
        try {
            String[] p = date.split("/");
            Calendar dob = Calendar.getInstance();
            dob.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]));
            Calendar today = Calendar.getInstance();
            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--;
            return age >= 18 && !dob.after(today);
        } catch (Exception e) { return false; }
    }

    private void initViews() {
        etFullName = findViewById(R.id.etFullName);
        etBirthday = findViewById(R.id.etBirthday);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tvErrorName = findViewById(R.id.tvErrorName);
        tvErrorBirthday = findViewById(R.id.tvErrorBirthday);
        tvErrorEmail = findViewById(R.id.tvErrorEmail);
        tvErrorPassword = findViewById(R.id.tvErrorPassword);
        tvErrorGender = findViewById(R.id.tvErrorGender);
        rgGender = findViewById(R.id.rgGender);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
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