package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.FirebaseManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private TextInputLayout tilCurrentPassword, tilNewPassword, tilConfirmPassword;
    private TextInputEditText etCurrentPassword, etNewPassword, etConfirmPassword;
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
        ImageView ivBack = findViewById(R.id.ivBackChangePassword);
        ivBack.setOnClickListener(v -> finish());
        tilCurrentPassword = findViewById(R.id.tilCurrentPassword);
        tilNewPassword      = findViewById(R.id.tilNewPassword);
        tilConfirmPassword  = findViewById(R.id.tilConfirmPassword);
        etCurrentPassword   = findViewById(R.id.etCurrentPassword);
        etNewPassword       = findViewById(R.id.etNewPassword);
        etConfirmPassword   = findViewById(R.id.etConfirmPassword);
        btnChangePassword   = findViewById(R.id.btnChangePassword);
        progressBar         = findViewById(R.id.progressBarChangePassword);
    }

    private void setupClickListeners() {
        btnChangePassword.setOnClickListener(v -> {
            if (validateForm()) changePasswordWithFirebase();
        });
    }

    private boolean validateForm() {
        String current  = getText(etCurrentPassword);
        String newPwd   = getText(etNewPassword);
        String confirm  = getText(etConfirmPassword);
        boolean valid   = true;

        tilCurrentPassword.setError(null);
        tilNewPassword.setError(null);
        tilConfirmPassword.setError(null);

        if (current.isEmpty()) {
            tilCurrentPassword.setError("Mật khẩu hiện tại không được để trống"); valid = false;
        }
        if (newPwd.isEmpty()) {
            tilNewPassword.setError("Mật khẩu mới không được để trống"); valid = false;
        } else if (newPwd.length() < 8) {
            tilNewPassword.setError("Mật khẩu mới phải có ít nhất 8 ký tự"); valid = false;
        } else if (newPwd.equals(current)) {
            tilNewPassword.setError("Mật khẩu mới không được trùng với mật khẩu hiện tại"); valid = false;
        }
        if (confirm.isEmpty()) {
            tilConfirmPassword.setError("Xác nhận mật khẩu không được để trống"); valid = false;
        } else if (!confirm.equals(newPwd)) {
            tilConfirmPassword.setError("Xác nhận mật khẩu phải trùng với mật khẩu mới"); valid = false;
        }
        return valid;
    }

    /** Dùng Firebase Auth re-authenticate → updatePassword() */
    private void changePasswordWithFirebase() {
        String currentPwd = getText(etCurrentPassword);
        String newPwd     = getText(etNewPassword);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        // Re-authenticate trước để đảm bảo bảo mật
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPwd);
        user.reauthenticate(credential)
            .addOnSuccessListener(unused ->
                user.updatePassword(newPwd)
                    .addOnSuccessListener(v -> {
                        setLoading(false);
                        Toast.makeText(this,
                            "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG).show();
                        // Sign out và về Login
                        FirebaseManager.clearSession(this);
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    })
            )
            .addOnFailureListener(e -> {
                setLoading(false);
                tilCurrentPassword.setError("Mật khẩu hiện tại không đúng");
                Toast.makeText(this, "Mật khẩu hiện tại không đúng", Toast.LENGTH_SHORT).show();
            });
    }

    private void setLoading(boolean loading) {
        btnChangePassword.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnChangePassword.setText(loading ? "Đang xử lý..." : "Đổi mật khẩu");
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
