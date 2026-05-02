package com.example.weconnect.activities;

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
import com.example.weconnect.api.FirebaseManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFullName, etBirthday, etEmail, etPassword;
    private TextView tvErrorName, tvErrorBirthday, tvErrorEmail, tvErrorPassword, tvErrorGender, tvBackToLogin;
    private RadioGroup rgGender;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupSmartValidation();

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

    // =====================================================================
    // Firebase Auth Register + Firestore user document
    // =====================================================================

    private void registerUser() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();

        int selectedId = rgGender.getCheckedRadioButtonId();
        RadioButton radioButton = findViewById(selectedId);
        String gender = radioButton.getText().toString();

        btnRegister.setEnabled(false);
        btnRegister.setText("Đang đăng ký...");

        // Bước 1: Tạo Firebase Auth account
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(authResult -> {
                String uid = authResult.getUser().getUid();
                // Bước 2: Tạo document users/{uid} trong Firestore
                createFirestoreUserProfile(uid, email, fullName, birthday, gender);
            })
            .addOnFailureListener(e -> {
                btnRegister.setEnabled(true);
                btnRegister.setText("Đăng ký");

                String msg = e.getMessage();
                Log.e("REGISTER_ERROR", msg != null ? msg : "unknown");

                if (msg != null && msg.contains("email address is already in use")) {
                    showError(tvErrorEmail, "Email này đã được sử dụng");
                } else if (msg != null && msg.contains("badly formatted")) {
                    showError(tvErrorEmail, "Email không đúng định dạng");
                } else if (msg != null && msg.contains("weak-password")) {
                    showError(tvErrorPassword, "Mật khẩu quá yếu (tối thiểu 8 ký tự)");
                } else {
                    Toast.makeText(this, "Đăng ký thất bại: " + msg, Toast.LENGTH_LONG).show();
                }
            });
    }

    /** Tạo document users/{uid} trong Firestore với đầy đủ thông tin */
    private void createFirestoreUserProfile(String uid, String email, String fullName,
                                             String birthday, String gender) {
        Map<String, Object> userDoc = new HashMap<>();
        userDoc.put("email", email);
        userDoc.put("fullName", fullName);
        userDoc.put("birthday", birthday);
        userDoc.put("gender", gender);
        userDoc.put("avatarUrl", "");
        userDoc.put("bio", "");
        userDoc.put("interestTags", "");
        userDoc.put("averageRating", 0.0f);
        userDoc.put("reputationScore", 0);
        userDoc.put("isBlocked", false);
        userDoc.put("role", 0); // 0 = user thường
        userDoc.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(userDoc)
            .addOnSuccessListener(aVoid -> {
                // Lưu session
                FirebaseManager.saveUserId(this, uid);
                FirebaseManager.saveUserName(this, fullName);

                Toast.makeText(this, "Đăng ký thành công!", Toast.LENGTH_SHORT).show();
                goToOnboarding();
            })
            .addOnFailureListener(e -> {
                // Auth đã tạo nhưng Firestore lỗi → vẫn cho vào Onboarding
                Log.e("FIRESTORE_ERROR", "Không tạo được profile: " + e.getMessage());
                FirebaseManager.saveUserId(this, uid);
                FirebaseManager.saveUserName(this, fullName);
                goToOnboarding();
            });
    }

    private void goToOnboarding() {
        Intent intent = new Intent(RegisterActivity.this, OnboardingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =====================================================================
    // Validation (giữ nguyên logic cũ)
    // =====================================================================

    private void setupSmartValidation() {
        etFullName.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(tvErrorName, "Họ tên không được để trống");
            } else if (s.length() < 8) {
                showError(tvErrorName, "Họ tên phải ít nhất 8 ký tự");
            } else {
                tvErrorName.setVisibility(View.GONE);
            }
        }));

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

        etPassword.addTextChangedListener(new SimpleTextWatcher(s -> {
            if (s.length() == 0) {
                showError(tvErrorPassword, "Mật khẩu không được để trống");
            } else if (s.length() < 8) {
                showError(tvErrorPassword, "Mật khẩu phải ít nhất 8 ký tự");
            } else {
                tvErrorPassword.setVisibility(View.GONE);
            }
        }));

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
        etFullName  = findViewById(R.id.etFullName);
        etBirthday  = findViewById(R.id.etBirthday);
        etEmail     = findViewById(R.id.etEmail);
        etPassword  = findViewById(R.id.etPassword);
        tvErrorName     = findViewById(R.id.tvErrorName);
        tvErrorBirthday = findViewById(R.id.tvErrorBirthday);
        tvErrorEmail    = findViewById(R.id.tvErrorEmail);
        tvErrorPassword = findViewById(R.id.tvErrorPassword);
        tvErrorGender   = findViewById(R.id.tvErrorGender);
        rgGender    = findViewById(R.id.rgGender);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);
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