package com.example.weconnect.presentation.ui;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.weconnect.R;
import com.example.weconnect.databinding.ActivityEditProfileBinding;
import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.presentation.viewmodel.ProfileViewModel;
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

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private ProfileViewModel viewModel;
    
    private static final int MAX_SELECTIONS = 5;
    private final List<String> selectedInterests = new ArrayList<>();

    private final Map<String, String[]> categories = new LinkedHashMap<String, String[]>() {{
        put("chipGroupSport", new String[]{
            "⚽ Bóng đá","🏀 Bóng rổ","🏸 Cầu lông","🏐 Bóng chuyền",
            "🎾 Tennis","🏊 Bơi lội","🚴 Đạp xe","🏃 Chạy bộ","🧘 Yoga","🏋️ Gym"
        });
        put("chipGroupEntertainment", new String[]{
            "🎬 Phim ảnh","🎵 Âm nhạc","🎮 Game","📚 Đọc sách",
            "🎭 Kịch nghệ","🎤 Karaoke","🎲 Board game","📸 Chụp ảnh"
        });
        put("chipGroupCreative", new String[]{
            "🎨 Vẽ tranh","✍️ Viết lách","🧶 Thủ công","🎸 Chơi nhạc cụ",
            "💃 Nhảy múa","🎥 Làm phim","🖥️ Lập trình","📐 Thiết kế"
        });
        put("chipGroupStudy", new String[]{
            "🎓 Đại học","📖 Tự học","🌍 Ngoại ngữ","💼 Kinh doanh",
            "🔬 Khoa học","📊 Data Science","🤖 AI/ML","📝 Ôn thi"
        });
        put("chipGroupSocial", new String[]{
            "☕ Cà phê","🍜 Ẩm thực","✈️ Du lịch","🌿 Thiên nhiên",
            "🐕 Thú cưng","🎪 Tình nguyện","💬 Giao lưu","🏕️ Camping"
        });
    }};

    private final int[] chipGroupIds = {
        R.id.chipGroupSport, R.id.chipGroupEntertainment,
        R.id.chipGroupCreative, R.id.chipGroupStudy, R.id.chipGroupSocial
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        setupInterestChips();
        setupClickListeners();
        setupObservers();

        String uid = FirebaseManager.getCurrentUserId();
        if (uid != null) {
            viewModel.loadProfile(uid);
        } else {
            binding.etDisplayName.setText(FirebaseManager.getUserName(this));
        }
    }

    private void setupObservers() {
        viewModel.userProfile.observe(this, profile -> {
            if (profile.get("fullName") != null)
                binding.etDisplayName.setText(profile.get("fullName").toString());
            if (profile.get("birthday") != null)
                binding.etBirthDate.setText(profile.get("birthday").toString());
            if (profile.get("bio") != null)
                binding.etBio.setText(profile.get("bio").toString());

            String gender = profile.get("gender") != null ? profile.get("gender").toString() : "";
            if ("Nam".equalsIgnoreCase(gender)) binding.chipGroupGender.check(R.id.chipMale);
            else if ("Nữ".equalsIgnoreCase(gender)) binding.chipGroupGender.check(R.id.chipFemale);
            else if ("Khác".equalsIgnoreCase(gender)) binding.chipGroupGender.check(R.id.chipOther);

            // Pre-select interests
            Object tagsObj = profile.get("interestTags");
            if (tagsObj instanceof List) {
                for (Object t : (List<?>) tagsObj) selectChipByText(t.toString());
            } else if (tagsObj instanceof String && !((String) tagsObj).isEmpty()) {
                for (String t : ((String) tagsObj).split(",")) selectChipByText(t.trim());
            }
        });

        viewModel.successMessage.observe(this, msg -> {
            Toast.makeText(EditProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
        });

        viewModel.error.observe(this, err -> {
            Toast.makeText(EditProfileActivity.this, "Lỗi: " + err, Toast.LENGTH_SHORT).show();
            binding.etDisplayName.setText(FirebaseManager.getUserName(EditProfileActivity.this));
        });

        viewModel.actionStatus.observe(this, success -> {
            if (success) {
                FirebaseManager.saveUserName(EditProfileActivity.this, getText(binding.etDisplayName));
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                    .edit().putString("user_interests", String.join(",", selectedInterests)).apply();
                setResult(RESULT_OK);
                finish();
            }
        });
    }

    private void setupInterestChips() {
        String[] keys = categories.keySet().toArray(new String[0]);
        for (int i = 0; i < chipGroupIds.length; i++) {
            ChipGroup group = findViewById(chipGroupIds[i]);
            if (group == null) continue;
            group.removeAllViews();
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
                chip.setOnCheckedChangeListener((b, checked) -> {
                    String tag = chip.getText().toString();
                    if (checked) {
                        if (selectedInterests.size() >= MAX_SELECTIONS) {
                            chip.setChecked(false);
                            Toast.makeText(this, "Tối đa " + MAX_SELECTIONS + " sở thích", Toast.LENGTH_SHORT).show();
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
                group.addView(chip);
            }
        }
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
        binding.ivBackEditProfile.setOnClickListener(v -> finish());
        binding.etBirthDate.setOnClickListener(v -> showDatePicker());

        binding.btnSaveProfile.setOnClickListener(v -> {
            String name = getText(binding.etDisplayName), birthDate = getText(binding.etBirthDate), bio = getText(binding.etBio);
            if (name.isEmpty()) { binding.etDisplayName.setError("Vui lòng nhập tên"); return; }

            String gender = "";
            int gId = binding.chipGroupGender.getCheckedChipId();
            if (gId == R.id.chipMale) gender = "Nam";
            else if (gId == R.id.chipFemale) gender = "Nữ";
            else if (gId == R.id.chipOther) gender = "Khác";

            saveToFirestore(name, birthDate, bio, gender, selectedInterests);
        });
    }

    private void saveToFirestore(String name, String birthday, String bio,
                                  String gender, List<String> interests) {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show(); return; }

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", name);
        data.put("birthday", birthday);
        data.put("bio", bio);
        data.put("gender", gender);
        data.put("interestTags", interests);

        viewModel.updateProfile(uid, data);
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this,
            (view, year, month, day) -> {
                binding.etBirthDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year));
            },
            c.get(Calendar.YEAR) - 20, c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ) {{
            getDatePicker().setMaxDate(System.currentTimeMillis());
        }}.show();
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
