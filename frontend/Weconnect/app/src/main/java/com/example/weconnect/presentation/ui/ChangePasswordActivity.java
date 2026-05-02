package com.example.weconnect.presentation.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.databinding.ActivityChangePasswordBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ChangePasswordActivity extends AppCompatActivity {

    private ActivityChangePasswordBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChangePasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupClickListeners();
    }

    private void setupClickListeners() {
        binding.ivBackChangePassword.setOnClickListener(v -> finish());
        binding.btnChangePassword.setOnClickListener(v -> {
            if (validateForm()) changePasswordWithFirebase();
        });
    }

    private boolean validateForm() {
        String current  = getText(binding.etCurrentPassword);
        String newPwd   = getText(binding.etNewPassword);
        String confirm  = getText(binding.etConfirmPassword);
        boolean valid   = true;

        binding.tilCurrentPassword.setError(null);
        binding.tilNewPassword.setError(null);
        binding.tilConfirmPassword.setError(null);

        if (current.isEmpty()) {
            binding.tilCurrentPassword.setError("Mật khẩu hiện tại không được để trống"); valid = false;
        }
        if (newPwd.isEmpty()) {
            binding.tilNewPassword.setError("Mật khẩu mới không được để trống"); valid = false;
        } else if (newPwd.length() < 8) {
            binding.tilNewPassword.setError("Mật khẩu mới phải có ít nhất 8 ký tự"); valid = false;
        } else if (newPwd.equals(current)) {
            binding.tilNewPassword.setError("Mật khẩu mới không được trùng với mật khẩu hiện tại"); valid = false;
        }
        if (confirm.isEmpty()) {
            binding.tilConfirmPassword.setError("Xác nhận mật khẩu không được để trống"); valid = false;
        } else if (!confirm.equals(newPwd)) {
            binding.tilConfirmPassword.setError("Xác nhận mật khẩu phải trùng với mật khẩu mới"); valid = false;
        }
        return valid;
    }

    private void changePasswordWithFirebase() {
        String currentPwd = getText(binding.etCurrentPassword);
        String newPwd     = getText(binding.etNewPassword);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPwd);
        user.reauthenticate(credential)
            .addOnSuccessListener(unused ->
                user.updatePassword(newPwd)
                    .addOnSuccessListener(v -> {
                        setLoading(false);
                        Toast.makeText(this,
                            "Đổi mật khẩu thành công! Vui lòng đăng nhập lại.",
                            Toast.LENGTH_LONG).show();
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
                binding.tilCurrentPassword.setError("Mật khẩu hiện tại không đúng");
                Toast.makeText(this, "Mật khẩu hiện tại không đúng", Toast.LENGTH_SHORT).show();
            });
    }

    private void setLoading(boolean loading) {
        binding.btnChangePassword.setEnabled(!loading);
        binding.progressBarChangePassword.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnChangePassword.setText(loading ? "Đang xử lý..." : "Đổi mật khẩu");
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
