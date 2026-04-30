package com.example.weconnect.profile.ui;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.profile.data.UserApiService;
import com.example.weconnect.core.data.ApiResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView ivBackEditProfile;
    private TextInputEditText etDisplayName;
    private TextInputEditText etBirthDate;
    private TextInputEditText etBio;
    private ChipGroup chipGroupGender;
    private MaterialButton btnSaveProfile;
    private static final int MAX_SELECTIONS = 5;
    private final List<String> selectedInterests = new ArrayList<>();

    // Danh sách sở thích giống OnboardingActivity
    private final Map<String, String[]> categories = new LinkedHashMap<String, String[]>() {{
        put("chipGroupSport", new String[]{
                "⚽ Bóng đá", "🏀 Bóng rổ", "🏸 Cầu lông", "🏐 Bóng chuyền",
                "🎾 Tennis", "🏊 Bơi lội", "🚴 Đạp xe", "🏃 Chạy bộ",
                "🧘 Yoga", "🏋️ Gym"
        });
        put("chipGroupEntertainment", new String[]{
                "🎬 Phim ảnh", "🎵 Âm nhạc", "🎮 Game", "📚 Đọc sách",
                "🎭 Kịch nghệ", "🎤 Karaoke", "🎲 Board game", "📸 Chụp ảnh"
        });
        put("chipGroupCreative", new String[]{
                "🎨 Vẽ tranh", "✍️ Viết lách", "🧶 Thủ công", "🎸 Chơi nhạc cụ",
                "💃 Nhảy múa", "🎥 Làm phim", "🖥️ Lập trình", "📐 Thiết kế"
        });
        put("chipGroupStudy", new String[]{
                "🎓 Đại học", "📖 Tự học", "🌍 Ngoại ngữ", "💼 Kinh doanh",
                "🔬 Khoa học", "📊 Data Science", "🤖 AI/ML", "📝 Ôn thi"
        });
        put("chipGroupSocial", new String[]{
                "☕ Cà phê", "🍜 Ẩm thực", "✈️ Du lịch", "🌿 Thiên nhiên",
                "🐕 Thú cưng", "🎪 Tình nguyện", "💬 Giao lưu", "🏕️ Camping"
        });
    }};

    // ChipGroups for interests
    private final int[] chipGroupIds = {
            R.id.chipGroupSport,
            R.id.chipGroupEntertainment,
            R.id.chipGroupCreative,
            R.id.chipGroupStudy,
            R.id.chipGroupSocial
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initViews();
        setupInterestChips();
        loadCurrentProfile();
        setupClickListeners();
    }

    private void initViews() {
        ivBackEditProfile = findViewById(R.id.ivBackEditProfile);
        etDisplayName = findViewById(R.id.etDisplayName);
        etBirthDate = findViewById(R.id.etBirthDate);
        etBio = findViewById(R.id.etBio);
        chipGroupGender = findViewById(R.id.chipGroupGender);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
    }

    private void setupInterestChips() {
        String[] keys = categories.keySet().toArray(new String[0]);

        for (int i = 0; i < chipGroupIds.length; i++) {
            ChipGroup chipGroup = findViewById(chipGroupIds[i]);
            if (chipGroup == null) continue;
            chipGroup.removeAllViews();

            String[] items = categories.get(keys[i]);
            if (items == null) continue;

            for (String item : items) {
                Chip chip = new Chip(this);
                chip.setText(item);
                chip.setCheckable(true);
                chip.setClickable(true);
                chip.setTextSize(14);
                chip.setChipCornerRadius(60f);
                chip.setChipStrokeWidth(2f);
                chip.setChipStrokeColor(ColorStateList.valueOf(0xFFE0E0E0));
                chip.setChipBackgroundColor(ColorStateList.valueOf(
                        getResources().getColor(R.color.card_surface, null)));
                chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                chip.setCheckedIconVisible(false);
                chip.setChipMinHeight(96f);
                chip.setPadding(16, 8, 16, 8);

                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    String tag = chip.getText().toString();
                    if (isChecked) {
                        if (selectedInterests.size() >= MAX_SELECTIONS) {
                            chip.setChecked(false);
                            Toast.makeText(this,
                                    "Tối đa " + MAX_SELECTIONS + " sở thích",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectedInterests.add(tag);
                        chip.setChipBackgroundColor(ColorStateList.valueOf(
                                getResources().getColor(R.color.primary_pink, null)));
                        chip.setTextColor(0xFFFFFFFF);
                        chip.setChipStrokeWidth(0f);
                    } else {
                        selectedInterests.remove(tag);
                        chip.setChipBackgroundColor(ColorStateList.valueOf(
                                getResources().getColor(R.color.card_surface, null)));
                        chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                        chip.setChipStrokeWidth(2f);
                    }
                });

                chipGroup.addView(chip);
            }
        }
    }

    private void loadCurrentProfile() {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            // Fallback: dùng dữ liệu local
            etDisplayName.setText(RetrofitClient.getUserName(this));
            return;
        }

        UserApiService apiService = RetrofitClient.getClient().create(UserApiService.class);

        // Load profile
        apiService.getMyProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    Map<String, Object> profile = response.body().getResult();

                    String fullName = profile.get("fullName") != null
                            ? profile.get("fullName").toString() : "";
                    String birthday = profile.get("birthday") != null
                            ? profile.get("birthday").toString() : "";
                    String bio = profile.get("bio") != null
                            ? profile.get("bio").toString() : "";
                    String gender = profile.get("gender") != null
                            ? profile.get("gender").toString() : "";
                    String interestTags = profile.get("interestTags") != null
                            ? profile.get("interestTags").toString() : "";

                    etDisplayName.setText(fullName);
                    etBirthDate.setText(birthday);
                    etBio.setText(bio);

                    // Pre-select gender
                    if ("Nam".equalsIgnoreCase(gender)) {
                        chipGroupGender.check(R.id.chipMale);
                    } else if ("Nữ".equalsIgnoreCase(gender)) {
                        chipGroupGender.check(R.id.chipFemale);
                    } else if ("Khác".equalsIgnoreCase(gender)) {
                        chipGroupGender.check(R.id.chipOther);
                    }

                    // Pre-select interests from backend
                    if (!interestTags.isEmpty()) {
                        String[] tags = interestTags.split(",");
                        for (String tag : tags) {
                            String trimmed = tag.trim();
                            selectChipByText(trimmed);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                etDisplayName.setText(RetrofitClient.getUserName(EditProfileActivity.this));
            }
        });
    }

    private void selectChipByText(String text) {
        for (int groupId : chipGroupIds) {
            ChipGroup group = findViewById(groupId);
            if (group == null) continue;
            for (int i = 0; i < group.getChildCount(); i++) {
                Chip chip = (Chip) group.getChildAt(i);
                if (chip.getText().toString().equals(text)) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
    }

    private void setupClickListeners() {
        ivBackEditProfile.setOnClickListener(v -> finish());

        etBirthDate.setOnClickListener(v -> showDatePicker());

        btnSaveProfile.setOnClickListener(v -> {
            String name = etDisplayName.getText() != null ? etDisplayName.getText().toString().trim() : "";
            String birthDate = etBirthDate.getText() != null ? etBirthDate.getText().toString().trim() : "";
            String bio = etBio.getText() != null ? etBio.getText().toString().trim() : "";

            if (name.isEmpty()) {
                etDisplayName.setError("Vui lòng nhập tên");
                return;
            }

            // Get selected gender
            String gender = "";
            int genderId = chipGroupGender.getCheckedChipId();
            if (genderId == R.id.chipMale) gender = "Nam";
            else if (genderId == R.id.chipFemale) gender = "Nữ";
            else if (genderId == R.id.chipOther) gender = "Khác";

            // Save to backend
            saveToBackend(name, birthDate, bio, gender, selectedInterests);
        });
    }

    private void saveToBackend(String name, String birthday, String bio,
                               String gender, List<String> interests) {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            Toast.makeText(this, "Đã lưu thay đổi thành công!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        UserApiService apiService = RetrofitClient.getClient().create(UserApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("birthday", birthday);
        body.put("bio", bio);
        body.put("gender", gender);
        body.put("interestTags", String.join(",", interests));

        apiService.updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful()) {
                    // Cập nhật local storage
                    RetrofitClient.saveUserName(EditProfileActivity.this, name);
                    // Cập nhật SharedPreferences interests
                    android.content.SharedPreferences prefs =
                            getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
                    prefs.edit().putString("user_interests", String.join(",", interests)).apply();

                    Toast.makeText(EditProfileActivity.this,
                            "Đã lưu thay đổi thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    Toast.makeText(EditProfileActivity.this,
                            "Lỗi khi lưu. Thử lại sau.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(EditProfileActivity.this,
                        "Lỗi kết nối. Thử lại sau.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 20;
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String date = String.format(Locale.getDefault(), "%02d/%02d/%04d",
                            selectedDay, selectedMonth + 1, selectedYear);
                    etBirthDate.setText(date);
                },
                year, month, day
        );

        datePickerDialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        datePickerDialog.show();
    }
}
