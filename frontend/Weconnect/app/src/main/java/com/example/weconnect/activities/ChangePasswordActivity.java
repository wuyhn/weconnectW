package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.websocket.WebSocketManager;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChangePasswordActivity extends AppCompatActivity {

    private ImageView ivBackChangePassword;
    private TextInputLayout tilCurrentPassword;
    private TextInputLayout tilNewPassword;
    private TextInputLayout tilConfirmPassword;
    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etConfirmPassword;
    private MaterialButton btnChangePassword;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_password);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        ivBackChangePassword = findViewById(R.id.ivBackChangePassword);
        tilCurrentPassword = findViewById(R.id.tilCurrentPassword);
        tilNewPassword = findViewById(R.id.tilNewPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        progressBar = findViewById(R.id.progressBarChangePassword);
    }

    private void setupClickListeners() {
        ivBackChangePassword.setOnClickListener(v -> finish());

        btnChangePassword.setOnClickListener(v -> {
            if (validateForm()) {
                callChangePasswordApi();
            }
        });
    }

    private boolean validateForm() {
        boolean isValid = true;

        String currentPassword = etCurrentPassword.getText() != null
                ? etCurrentPassword.getText().toString().trim() : "";
        String newPassword = etNewPassword.getText() != null
                ? etNewPassword.getText().toString().trim() : "";
        String confirmPassword = etConfirmPassword.getText() != null
                ? etConfirmPassword.getText().toString().trim() : "";

        // Clear previous errors
        tilCurrentPassword.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        if (currentPassword.isEmpty()) {
            tilCurrentPassword.setError("Mật khẩu hiện tại không được để trống");
            isValid = false;
        }

        if (newPassword.isEmpty()) {
            tilNewPassword.setError("Mật khẩu mới không được để trống");
            isValid = false;
        } else if (newPassword.length() < 8) {
            tilNewPassword.setError("Mật khẩu mới phải có ít nhất 8 ký tự");
            isValid = false;
        }

        if (confirmPassword.isEmpty()) {
            tilConfirmPassword.setError("Xác nhận mật khẩu mới không được để trống");
            isValid = false;
        } else if (!confirmPassword.equals(newPassword)) {
            tilConfirmPassword.setError("Xác nhận mật khẩu mới phải trùng với mật khẩu mới");
            isValid = false;
        }

        if (!newPassword.isEmpty() && newPassword.equals(currentPassword)) {
            tilNewPassword.setError("Mật khẩu mới không được trùng với mật khẩu hiện tại");
            isValid = false;
        }

        return isValid;
    }

    private void callChangePasswordApi() {
        String currentPassword = etCurrentPassword.getText().toString().trim();
        String newPassword = etNewPassword.getText().toString().trim();

        // Show loading, disable button
        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);

        UserApiService apiService = RetrofitClient.getClient().create(UserApiService.class);
        apiService.changePassword(body).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    // Thành công — thông báo rồi logout
                    Toast.makeText(ChangePasswordActivity.this,
                            "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG).show();
                    logoutAndGoToLogin();
                } else {
                    // Parse error message từ backend
                    String errorMsg = "Đổi mật khẩu thất bại";
                    try {
                        if (response.errorBody() != null) {
                            String errorJson = response.errorBody().string();
                            JSONObject jsonObject = new JSONObject(errorJson);
                            errorMsg = jsonObject.optString("message", errorMsg);
                        } else if (response.body() != null) {
                            errorMsg = response.body().getMessage();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(ChangePasswordActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(ChangePasswordActivity.this,
                        "Không thể kết nối đến server. Vui lòng thử lại.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        btnChangePassword.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnChangePassword.setText(loading ? "Đang xử lý..." : "Đổi mật khẩu");
    }

    private void logoutAndGoToLogin() {
        // Clear session
        WebSocketManager.getInstance().disconnect();
        RetrofitClient.clearSession(this);

        // Chuyển về LoginActivity, xóa hết back stack
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
