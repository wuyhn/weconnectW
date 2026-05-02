package com.example.weconnect.presentation.ui;
import com.example.weconnect.presentation.ui.*;

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
import com.example.weconnect.data.repository.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import androidx.lifecycle.ViewModelProvider;
import com.example.weconnect.databinding.ActivityRegisterBinding;
import com.example.weconnect.presentation.viewmodel.AuthViewModel;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private AuthViewModel authViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authViewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        setupSmartValidation();
        observeViewModel();

        binding.btnRegister.setOnClickListener(v -> {
            checkFieldsOnSubmit();
            if (validateAllFields()) {
                String email = binding.etEmail.getText().toString().trim();
                String password = binding.etPassword.getText().toString().trim();
                String fullName = binding.etFullName.getText().toString().trim();
                String birthday = binding.etBirthday.getText().toString().trim();

                int selectedId = binding.rgGender.getCheckedRadioButtonId();
                RadioButton radioButton = findViewById(selectedId);
                String gender = radioButton.getText().toString();

                authViewModel.register(email, password, fullName, birthday, gender);
            } else {
                Toast.makeText(this, "Vui lòng hoàn thiện đúng thông tin", Toast.LENGTH_SHORT).show();
            }
        });

        binding.tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void observeViewModel() {
        authViewModel.authState.observe(this, state -> {
            switch (state) {
                case LOADING:
                    binding.btnRegister.setEnabled(false);
                    binding.btnRegister.setText("Đang đăng ký...");
                    break;
                case SUCCESS:
                    Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                    goToOnboarding();
                    break;
                case ERROR_EMAIL:
                case ERROR_PASSWORD:
                case ERROR_GENERAL:
                    binding.btnRegister.setEnabled(true);
                    binding.btnRegister.setText("Đăng ký");
                    break;
            }
        });

        authViewModel.errorMessage.observe(this, msg -> {
            AuthViewModel.AuthState state = authViewModel.authState.getValue();
            if (state == AuthViewModel.AuthState.ERROR_EMAIL) {
                showError(binding.tvErrorEmail, msg);
            } else if (state == AuthViewModel.AuthState.ERROR_PASSWORD) {
                showError(binding.tvErrorPassword, msg);
            } else if (state == AuthViewModel.AuthState.ERROR_GENERAL) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
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
        binding.etFullName.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(binding.tvErrorName, "Họ tên không được để trống");
            } else if (s.length() < 8) {
                showError(binding.tvErrorName, "Họ tên phải ít nhất 8 ký tự");
            } else {
                binding.tvErrorName.setVisibility(View.GONE);
            }
        }));

        binding.etBirthday.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().equals(current)) {
                    String clean = s.toString().replaceAll("[^\\d]", "");
                    String formatted = clean;
                    if (clean.length() >= 2 && clean.length() < 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2);
                    else if (clean.length() >= 4) formatted = clean.substring(0, 2) + "/" + clean.substring(2, 4) + "/" + clean.substring(4);

                    current = formatted;
                    binding.etBirthday.setText(current);
                    binding.etBirthday.setSelection(current.length());

                    if (current.isEmpty()) {
                        showError(binding.tvErrorBirthday, "Ngày sinh không được để trống");
                    } else if (current.length() < 10) {
                        showError(binding.tvErrorBirthday, "Vui lòng nhập đủ ngày sinh");
                    } else {
                        if (isValidAge(current)) binding.tvErrorBirthday.setVisibility(View.GONE);
                        else showError(binding.tvErrorBirthday, "Bạn phải từ 18 tuổi trở lên");
                    }
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.etEmail.addTextChangedListener(new SimpleTextWatcher(s -> {
            String email = s.toString().trim();
            if (email.isEmpty()) {
                showError(binding.tvErrorEmail, "Email không được để trống");
            } else if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.com$")) {
                showError(binding.tvErrorEmail, "Email phải đúng định dạng");
            } else {
                binding.tvErrorEmail.setVisibility(View.GONE);
            }
        }));

        binding.etPassword.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(binding.tvErrorPassword, "Mật khẩu không được để trống");
            } else if (s.length() < 8) {
                showError(binding.tvErrorPassword, "Mật khẩu phải ít nhất 8 ký tự");
            } else {
                binding.tvErrorPassword.setVisibility(View.GONE);
            }
        }));

        binding.rgGender.setOnCheckedChangeListener((group, checkedId) -> binding.tvErrorGender.setVisibility(View.GONE));
    }

    private void showError(TextView tv, String message) {
        tv.setText("⚠ " + message);
        tv.setVisibility(View.VISIBLE);
    }

    private void checkFieldsOnSubmit() {
        if (binding.etFullName.getText().toString().isEmpty()) showError(binding.tvErrorName, "Họ tên không được để trống");
        if (binding.etBirthday.getText().toString().isEmpty()) showError(binding.tvErrorBirthday, "Ngày sinh không được để trống");
        if (binding.etEmail.getText().toString().isEmpty()) showError(binding.tvErrorEmail, "Email không được để trống");
        if (binding.etPassword.getText().toString().isEmpty()) showError(binding.tvErrorPassword, "Mật khẩu không được để trống");
        if (binding.rgGender.getCheckedRadioButtonId() == -1) showError(binding.tvErrorGender, "Vui lòng chọn giới tính");
    }

    private boolean validateAllFields() {
        return binding.tvErrorName.getVisibility() == View.GONE &&
                binding.tvErrorBirthday.getVisibility() == View.GONE &&
                binding.tvErrorEmail.getVisibility() == View.GONE &&
                binding.tvErrorPassword.getVisibility() == View.GONE &&
                binding.tvErrorGender.getVisibility() == View.GONE &&
                binding.rgGender.getCheckedRadioButtonId() != -1 &&
                !binding.etEmail.getText().toString().isEmpty();
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

    interface TextChangedListener { void onTextChanged(CharSequence s); }
    class SimpleTextWatcher implements TextWatcher {
        private final TextChangedListener listener;
        public SimpleTextWatcher(TextChangedListener l) { this.listener = l; }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { listener.onTextChanged(s); }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}