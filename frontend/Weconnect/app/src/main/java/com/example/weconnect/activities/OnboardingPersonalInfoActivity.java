package com.example.weconnect.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.data.AdministrativeLocationData;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OnboardingPersonalInfoActivity extends AppCompatActivity {

    private static final String AGE_RANGE_ERROR_MESSAGE =
            "Ứng dụng WeConnect chỉ dành cho người dùng từ 16 đến 60 tuổi!";

    private TextInputEditText etFullName, etBirthday, etProvince;
    private TextView tvErrorName, tvErrorBirthday, tvErrorGender, tvErrorProvince;
    private MaterialButton btnGenderMale, btnGenderFemale, btnGenderOther, btnContinue;

    private String selectedGender = null;
    private String selectedProvinceId = null;
    private String selectedProvinceName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_personal_info);

        etFullName = findViewById(R.id.etFullName);
        etBirthday = findViewById(R.id.etBirthday);
        etProvince = findViewById(R.id.etProvince);
        tvErrorName = findViewById(R.id.tvErrorName);
        tvErrorBirthday = findViewById(R.id.tvErrorBirthday);
        tvErrorGender = findViewById(R.id.tvErrorGender);
        tvErrorProvince = findViewById(R.id.tvErrorProvince);
        btnGenderMale = findViewById(R.id.btnGenderMale);
        btnGenderFemale = findViewById(R.id.btnGenderFemale);
        btnGenderOther = findViewById(R.id.btnGenderOther);
        btnContinue = findViewById(R.id.btnContinue);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        setupGenderButtons();
        setupProvincePicker();
        setupBirthdayFormatter();
        setupValidation();

        btnContinue.setOnClickListener(v -> {
            if (!validateAll()) {
                return;
            }
            updateProfile();
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

    private void setupProvincePicker() {
        View.OnClickListener openProvincePicker = v -> showProvinceBottomSheet();
        TextInputLayout provinceLayout = findViewById(R.id.tilProvince);
        provinceLayout.setOnClickListener(openProvincePicker);
        provinceLayout.setEndIconOnClickListener(openProvincePicker);
        etProvince.setOnClickListener(openProvincePicker);
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

        if (bday.isEmpty()) {
            showBirthdayError("Ngày sinh không được để trống");
            ok = false;
        } else if (bday.length() < 10) {
            showBirthdayError("Vui lòng nhập đủ ngày sinh");
            ok = false;
        } else {
            Integer birthYear = extractBirthYear(bday);
            if (birthYear == null || !validateAge(birthYear)) {
                showBirthdayError(AGE_RANGE_ERROR_MESSAGE);
                Toast.makeText(this, AGE_RANGE_ERROR_MESSAGE, Toast.LENGTH_LONG).show();
                ok = false;
            } else {
                etBirthday.setError(null);
                hide(tvErrorBirthday);
            }
        }

        if (selectedGender == null) { show(tvErrorGender, "Vui lòng chọn giới tính"); ok = false; }

        if (selectedProvinceId == null || selectedProvinceName == null) {
            showProvinceError("Vui lòng chọn tỉnh/thành phố");
            ok = false;
        } else {
            hideProvinceError();
        }

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
        body.put("provinceId", selectedProvinceId);
        body.put("provinceName", selectedProvinceName);

        RetrofitClient.getClient().create(UserApiService.class)
                .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> resp) {
                        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                            RetrofitClient.saveUserName(OnboardingPersonalInfoActivity.this, fullName);
                            RetrofitClient.saveUserProvince(OnboardingPersonalInfoActivity.this, selectedProvinceId, selectedProvinceName);
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

    private void showProvinceBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpPx(20), dpPx(10), dpPx(20), dpPx(12));
        root.setBackgroundColor(Color.WHITE);

        View dragHandle = new View(this);
        dragHandle.setBackground(createRoundedDrawable(0xFFD4CEC5, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dpPx(44), dpPx(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dpPx(18);
        root.addView(dragHandle, handleParams);

        TextView title = new TextView(this);
        title.setText("Chọn tỉnh/thành phố");
        title.setTextColor(0xFF1A1208);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Tìm kiếm tỉnh/thành phố");
        searchInput.setHintTextColor(0xFF9A9082);
        searchInput.setTextColor(0xFF1A1208);
        searchInput.setTextSize(15);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dpPx(14), 0, dpPx(14), 0);
        searchInput.setBackground(createRoundedDrawable(0xFFF4F1EC, 10));

        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpPx(46)
        );
        searchParams.topMargin = dpPx(16);
        searchParams.bottomMargin = dpPx(12);
        root.addView(searchInput, searchParams);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout provinceList = new LinearLayout(this);
        provinceList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(provinceList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        dialog.setContentView(root);
        renderProvinceList(provinceList, "", dialog);
        searchInput.addTextChangedListener(new SimpleWatcher(query ->
                renderProvinceList(provinceList, query.toString(), dialog)));

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet == null) return;

            int targetHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.7f);
            ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
            params.height = targetHeight;
            bottomSheet.setLayoutParams(params);

            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setPeekHeight(targetHeight);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        dialog.show();
    }

    private void renderProvinceList(LinearLayout container, String query, BottomSheetDialog dialog) {
        container.removeAllViews();

        String normalizedQuery = normalizeSearchText(query);
        List<AdministrativeLocationData.Province> filtered = new ArrayList<>();
        for (AdministrativeLocationData.Province province : AdministrativeLocationData.provinces()) {
            if (normalizedQuery.isEmpty()
                    || normalizeSearchText(province.name).contains(normalizedQuery)) {
                filtered.add(province);
            }
        }

        if (filtered.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Không tìm thấy tỉnh/thành phố");
            emptyView.setTextColor(0xFF9A9082);
            emptyView.setTextSize(15);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, dpPx(28), 0, dpPx(28));
            container.addView(emptyView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            return;
        }

        for (AdministrativeLocationData.Province province : filtered) {
            boolean selected = province.id.equals(selectedProvinceId);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpPx(4), 0, dpPx(4), 0);
            row.setMinimumHeight(dpPx(52));
            row.setBackgroundResource(selectableItemBackground());

            TextView nameView = new TextView(this);
            nameView.setText(province.name);
            nameView.setTextColor(selected ? 0xFF7A2450 : 0xFF1A1208);
            nameView.setTextSize(16);
            nameView.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.addView(nameView, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            ImageView checkIcon = new ImageView(this);
            checkIcon.setImageResource(R.drawable.ic_check);
            checkIcon.setColorFilter(0xFF7A2450);
            checkIcon.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(checkIcon, new LinearLayout.LayoutParams(dpPx(22), dpPx(22)));

            row.setOnClickListener(v -> {
                selectedProvinceId = province.id;
                selectedProvinceName = province.name;
                etProvince.setText(province.name);
                hideProvinceError();
                dialog.dismiss();
            });

            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpPx(52)
            ));
        }
    }

    private Integer extractBirthYear(String date) {
        try {
            String[] p = date.split("/");
            if (p.length != 3) return null;
            Calendar dob = Calendar.getInstance();
            dob.setLenient(false);
            dob.set(Integer.parseInt(p[2]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[0]));
            dob.getTime();
            return dob.get(Calendar.YEAR);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validateAge(int birthYear) {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        // Tuổi nhỏ nhất là 16, nên năm sinh lớn nhất được phép là năm hiện tại trừ 16.
        // Ví dụ năm hiện tại 2026 thì người sinh sau năm 2010 chưa đủ tuổi dùng ứng dụng.
        int maxValidBirthYear = currentYear - 16;

        // Tuổi lớn nhất là 60, nên năm sinh nhỏ nhất được phép là năm hiện tại trừ 60.
        // Ví dụ năm hiện tại 2026 thì người sinh trước năm 1966 vượt quá giới hạn tuổi.
        int minValidBirthYear = currentYear - 60;

        return birthYear >= minValidBirthYear && birthYear <= maxValidBirthYear;
    }

    private void showBirthdayError(String message) {
        etBirthday.setError(message);
        show(tvErrorBirthday, message);
    }

    private void showProvinceError(String message) {
        etProvince.setError(message);
        show(tvErrorProvince, message);
    }

    private void hideProvinceError() {
        etProvince.setError(null);
        hide(tvErrorProvince);
    }

    private String normalizeSearchText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private GradientDrawable createRoundedDrawable(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dpPx(radiusDp));
        return drawable;
    }

    private int selectableItemBackground() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId;
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
