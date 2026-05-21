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
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OnboardingPersonalInfoActivity extends AppCompatActivity {

    private TextInputEditText etFullName, etBirthday;
    private TextView tvErrorName, tvErrorBirthday, tvErrorGender;
    private MaterialButton btnGenderMale, btnGenderFemale, btnGenderOther, btnContinue;

    private String selectedGender = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_personal_info);

        etFullName = findViewById(R.id.etFullName);
        etBirthday = findViewById(R.id.etBirthday);
        tvErrorName = findViewById(R.id.tvErrorName);
        tvErrorBirthday = findViewById(R.id.tvErrorBirthday);
        tvErrorGender = findViewById(R.id.tvErrorGender);
        btnGenderMale = findViewById(R.id.btnGenderMale);
        btnGenderFemale = findViewById(R.id.btnGenderFemale);
        btnGenderOther = findViewById(R.id.btnGenderOther);
        btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        setupGenderButtons();
        setupBirthdayFormatter();
        setupValidation();

        btnContinue.setOnClickListener(v -> {
            if (validateAll()) updateProfile();
        });
    }

    private void setupGenderButtons() {
        View.OnClickListener genderClick = v -> {
            selectedGender = ((MaterialButton) v).getText().toString();
            updateGenderUI((MaterialButton) v);
            tvErrorGender.setVisibility(View.GONE);
        };
        btnGenderMale.setOnClickListener(genderClick);
        btnGenderFemale.setOnClickListener(genderClick);
        btnGenderOther.setOnClickListener(genderClick);
    }

    private void updateGenderUI(MaterialButton selected) {
        int dark = 0xFF7A7268;
        int strokeDefault = 0xFFC8C2B8;
        for (MaterialButton btn : new MaterialButton[]{btnGenderMale, btnGenderFemale, btnGenderOther}) {
            if (btn == selected) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(dark));
                btn.setTextColor(0xFFFFFFFF);
                btn.setStrokeWidth(0);
            } else {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x00000000));
                btn.setTextColor(0xFF3D3426);
                btn.setStrokeWidth(dpPx(1));
                btn.setStrokeColor(android.content.res.ColorStateList.valueOf(strokeDefault));
            }
        }
    }

    private void setupBirthdayFormatter() {
        etBirthday.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.toString().equals(current)) return;
                String clean = s.toString().replaceAll("[^\\d]", "");
                if (clean.length() > 8) clean = clean.substring(0, 8);
                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i == 2 || i == 4) formatted.append('/');
                    formatted.append(clean.charAt(i));
                }
                current = formatted.toString();
                etBirthday.setText(current);
                etBirthday.setSelection(current.length());
            }
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupValidation() {
        etFullName.addTextChangedListener(new SimpleWatcher(s -> {
            if (s.length() == 0) show(tvErrorName, "Họ tên không được để trống");
            else if (s.length() < 2) show(tvErrorName, "Họ tên quá ngắn");
            else hide(tvErrorName);
        }));
    }

    private boolean validateAll() {
        boolean ok = true;
        String name = etFullName.getText().toString().trim();
        String bday = etBirthday.getText().toString().trim();

        if (name.isEmpty()) { show(tvErrorName, "Họ tên không được để trống"); ok = false; }
        else if (name.length() < 2) { show(tvErrorName, "Họ tên quá ngắn"); ok = false; }

        if (bday.isEmpty()) { show(tvErrorBirthday, "Ngày sinh không được để trống"); ok = false; }
        else if (bday.length() < 10) { show(tvErrorBirthday, "Vui lòng nhập đủ ngày sinh"); ok = false; }
        else if (!isValidAge(bday)) { show(tvErrorBirthday, "Bạn phải từ 18 tuổi trở lên"); ok = false; }

        if (selectedGender == null) { show(tvErrorGender, "Vui lòng chọn giới tính"); ok = false; }
        return ok;
    }

    private void updateProfile() {
        btnContinue.setEnabled(false);
        btnContinue.setText("Đang xử lý...");

        String fullName = etFullName.getText().toString().trim();
        String birthday = etBirthday.getText().toString().trim();

        RetrofitClient.loadToken(this);

        Map<String, Object> body = new HashMap<>();
        body.put("fullName", fullName);
        body.put("birthday", birthday);
        body.put("gender", selectedGender);

        RetrofitClient.getClient().create(UserApiService.class)
                .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                            RetrofitClient.saveUserName(OnboardingPersonalInfoActivity.this, fullName);
                            startActivity(new Intent(OnboardingPersonalInfoActivity.this, OnboardingAvatarActivity.class));
                            finish();
                        } else {
                            btnContinue.setEnabled(true);
                            btnContinue.setText("Tiếp tục →");
                            String msg = (resp.body() != null) ? resp.body().getMessage() : "Cập nhật thất bại";
                            Toast.makeText(OnboardingPersonalInfoActivity.this, msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        btnContinue.setEnabled(true);
                        btnContinue.setText("Tiếp tục →");
                        Toast.makeText(OnboardingPersonalInfoActivity.this,
                                "Lỗi kết nối. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidAge(String date) {
        try {
            String[] p = date.split("/");
            if (p.length != 3) return false;
            Calendar dob = Calendar.getInstance();
            dob.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]));
            Calendar today = Calendar.getInstance();
            if (dob.after(today)) return false;
            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--;
            return age >= 18;
        } catch (Exception e) { return false; }
    }

    private void show(TextView tv, String msg) { tv.setText(msg); tv.setVisibility(View.VISIBLE); }
    private void hide(TextView tv) { tv.setVisibility(View.GONE); }
    private int dpPx(int dp) { return Math.round(dp * getResources().getDisplayMetrics().density); }

    interface OnChanged { void changed(CharSequence s); }
    static class SimpleWatcher implements TextWatcher {
        private final OnChanged l;
        SimpleWatcher(OnChanged l) { this.l = l; }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { l.changed(s); }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void afterTextChanged(Editable s) {}
    }
}
