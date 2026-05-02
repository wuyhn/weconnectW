package com.example.weconnect.activities;

import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreUserRepository;
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

public class EditProfileActivity extends AppCompatActivity {

    private TextInputEditText etDisplayName, etBirthDate, etBio;
    private ChipGroup chipGroupGender;
    private MaterialButton btnSaveProfile;
    private static final int MAX_SELECTIONS = 5;
    private final List<String> selectedInterests = new ArrayList<>();
    private FirestoreUserRepository userRepo;

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
        setContentView(R.layout.activity_edit_profile);

        userRepo = new FirestoreUserRepository();
        initViews();
        setupInterestChips();
        loadCurrentProfile();
        setupClickListeners();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.ivBackEditProfile);
        ivBack.setOnClickListener(v -> finish());
        etDisplayName  = findViewById(R.id.etDisplayName);
        etBirthDate    = findViewById(R.id.etBirthDate);
        etBio          = findViewById(R.id.etBio);
        chipGroupGender = findViewById(R.id.chipGroupGender);
        btnSaveProfile  = findViewById(R.id.btnSaveProfile);
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

    /** Load profile từ Firestore users/{uid} */
    private void loadCurrentProfile() {
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) {
            etDisplayName.setText(FirebaseManager.getUserName(this));
            return;
        }

        userRepo.getUserProfile(uid, new FirestoreUserRepository.ProfileCallback() {
            @Override public void onSuccess(Map<String, Object> profile) {
                runOnUiThread(() -> {
                    if (profile.get("fullName") != null)
                        etDisplayName.setText(profile.get("fullName").toString());
                    if (profile.get("birthday") != null)
                        etBirthDate.setText(profile.get("birthday").toString());
                    if (profile.get("bio") != null)
                        etBio.setText(profile.get("bio").toString());

                    String gender = profile.get("gender") != null ? profile.get("gender").toString() : "";
                    if ("Nam".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipMale);
                    else if ("Nữ".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipFemale);
                    else if ("Khác".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipOther);

                    // Pre-select interests
                    Object tagsObj = profile.get("interestTags");
                    if (tagsObj instanceof List) {
                        for (Object t : (List<?>) tagsObj) selectChipByText(t.toString());
                    } else if (tagsObj instanceof String && !((String) tagsObj).isEmpty()) {
                        for (String t : ((String) tagsObj).split(",")) selectChipByText(t.trim());
                    }
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() ->
                    etDisplayName.setText(FirebaseManager.getUserName(EditProfileActivity.this))
                );
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
        etBirthDate.setOnClickListener(v -> showDatePicker());

        btnSaveProfile.setOnClickListener(v -> {
            String name = getText(etDisplayName), birthDate = getText(etBirthDate), bio = getText(etBio);
            if (name.isEmpty()) { etDisplayName.setError("Vui lòng nhập tên"); return; }

            String gender = "";
            int gId = chipGroupGender.getCheckedChipId();
            if (gId == R.id.chipMale) gender = "Nam";
            else if (gId == R.id.chipFemale) gender = "Nữ";
            else if (gId == R.id.chipOther) gender = "Khác";

            saveToFirestore(name, birthDate, bio, gender, selectedInterests);
        });
    }

    /** Lưu profile vào Firestore users/{uid} */
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

        userRepo.updateProfile(uid, data, new FirestoreUserRepository.ActionCallback() {
            @Override public void onSuccess(String msg) {
                // Cập nhật local cache
                FirebaseManager.saveUserName(EditProfileActivity.this, name);
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                    .edit().putString("user_interests", String.join(",", interests)).apply();
                runOnUiThread(() -> {
                    Toast.makeText(EditProfileActivity.this, "Đã lưu thay đổi thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() ->
                    Toast.makeText(EditProfileActivity.this, "Lỗi khi lưu: " + err, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this,
            (view, year, month, day) -> {
                etBirthDate.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month + 1, year));
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
