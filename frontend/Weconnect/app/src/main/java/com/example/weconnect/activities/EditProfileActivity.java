package com.example.weconnect.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.weconnect.R;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView ivEditProfileAvatar;
    private TextInputEditText etDisplayName, etBirthDate, etBio;
    private ChipGroup chipGroupGender;
    private MaterialButton btnSaveProfile;
    private TextView tvInterestCount;

    private static final int MAX_SELECTIONS = 5;
    private final List<String> selectedInterests = new ArrayList<>();
    private String currentAvatarUrl = "";

    private final Map<String, String[]> categories = new LinkedHashMap<String, String[]>() {{
        put("chipGroupSport", new String[]{
                "⚽ Bóng đá", "🏀 Bóng rổ", "🏸 Cầu lông", "🏐 Bóng chuyền",
                "🎾 Tennis", "🏊 Bơi lội", "🚴 Đạp xe", "🏃 Chạy bộ", "🧘 Yoga", "🏋️ Gym"
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

    private final int[] chipGroupIds = {
            R.id.chipGroupSport, R.id.chipGroupEntertainment,
            R.id.chipGroupCreative, R.id.chipGroupStudy, R.id.chipGroupSocial
    };

    private final int[] categoryRowIds = {
            R.id.rowCategorySport, R.id.rowCategoryEntertainment,
            R.id.rowCategoryCreative, R.id.rowCategoryStudy, R.id.rowCategorySocial
    };

    private final int[] chevronIds = {
            R.id.ivChevronSport, R.id.ivChevronEntertainment,
            R.id.ivChevronCreative, R.id.ivChevronStudy, R.id.ivChevronSocial
    };

    private final boolean[] categoryExpanded = {false, false, false, false, false};

    private ActivityResultLauncher<Intent> imagePicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        registerImagePicker();
        initViews();
        setupInterestChips();
        setupCategoryHeaders();
        loadCurrentProfile();
        setupClickListeners();
    }

    private void registerImagePicker() {
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ignored) {}
                            uploadAvatar(uri);
                        }
                    }
                });
    }

    private void initViews() {
        ivEditProfileAvatar = findViewById(R.id.ivEditProfileAvatar);
        etDisplayName = findViewById(R.id.etDisplayName);
        etBirthDate = findViewById(R.id.etBirthDate);
        etBio = findViewById(R.id.etBio);
        chipGroupGender = findViewById(R.id.chipGroupGender);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        tvInterestCount = findViewById(R.id.tvInterestCount);

        currentAvatarUrl = RetrofitClient.getAvatarUrl(this);
        displayAvatar(currentAvatarUrl);
    }

    private void displayAvatar(String url) {
        if (ivEditProfileAvatar == null) return;
        if (url != null && !url.isEmpty()) {
            String displayUrl = url.startsWith("/")
                    ? RetrofitClient.getBaseUrl() + url.substring(1) : url;
            Glide.with(this)
                    .load(displayUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(ivEditProfileAvatar);
        } else {
            Glide.with(this)
                    .load(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivEditProfileAvatar);
        }
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
                chip.setTextSize(13f);
                chip.setChipCornerRadius(60f);
                chip.setChipStrokeWidth(1.5f);
                chip.setChipStrokeColor(ColorStateList.valueOf(0xFFDDD9D4));
                chip.setChipBackgroundColor(ColorStateList.valueOf(
                        getResources().getColor(R.color.soft_beige, null)));
                chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                chip.setCheckedIconVisible(false);

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
                                getResources().getColor(R.color.soft_beige, null)));
                        chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                        chip.setChipStrokeWidth(1.5f);
                    }
                    updateInterestCount();
                });

                chipGroup.addView(chip);
            }
        }
    }

    private void setupCategoryHeaders() {
        for (int i = 0; i < categoryRowIds.length; i++) {
            final int index = i;
            View row = findViewById(categoryRowIds[i]);
            if (row != null) {
                row.setOnClickListener(v -> toggleCategory(index));
            }
        }
    }

    private void toggleCategory(int index) {
        // Collapse all other categories first (one open at a time)
        for (int i = 0; i < chipGroupIds.length; i++) {
            if (i == index) continue;
            if (categoryExpanded[i]) {
                categoryExpanded[i] = false;
                ChipGroup group = findViewById(chipGroupIds[i]);
                ImageView chevron = findViewById(chevronIds[i]);
                if (group != null) group.setVisibility(View.GONE);
                if (chevron != null) chevron.setRotation(0f);
            }
        }

        categoryExpanded[index] = !categoryExpanded[index];
        ChipGroup targetGroup = findViewById(chipGroupIds[index]);
        ImageView targetChevron = findViewById(chevronIds[index]);
        if (targetGroup != null) {
            targetGroup.setVisibility(categoryExpanded[index] ? View.VISIBLE : View.GONE);
        }
        if (targetChevron != null) {
            targetChevron.setRotation(categoryExpanded[index] ? 90f : 0f);
        }
    }

    private void updateInterestCount() {
        if (tvInterestCount != null) {
            tvInterestCount.setText(selectedInterests.size() + " / " + MAX_SELECTIONS);
        }
    }

    private void loadCurrentProfile() {
        RetrofitClient.loadToken(this);
        if (RetrofitClient.getAuthToken() == null) {
            etDisplayName.setText(RetrofitClient.getUserName(this));
            return;
        }

        UserApiService apiService = RetrofitClient.getClient().create(UserApiService.class);
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
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : "";

                    etDisplayName.setText(fullName);
                    etBirthDate.setText(birthday);
                    etBio.setText(bio);

                    // Sync avatar from server (source of truth)
                    if (!avatarUrl.equals(currentAvatarUrl)) {
                        currentAvatarUrl = avatarUrl;
                        RetrofitClient.saveAvatarUrl(EditProfileActivity.this, avatarUrl);
                        displayAvatar(avatarUrl);
                    }

                    // Pre-select gender
                    if ("Nam".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipMale);
                    else if ("Nữ".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipFemale);
                    else if ("Khác".equalsIgnoreCase(gender)) chipGroupGender.check(R.id.chipOther);

                    // Pre-select interests
                    if (!interestTags.isEmpty()) {
                        String[] tags = interestTags.split(",");
                        for (String tag : tags) selectChipByText(tag.trim());
                        updateInterestCount();
                        autoExpandFirstCategoryWithSelections();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                etDisplayName.setText(RetrofitClient.getUserName(EditProfileActivity.this));
            }
        });
    }

    private void autoExpandFirstCategoryWithSelections() {
        for (int i = 0; i < chipGroupIds.length; i++) {
            ChipGroup group = findViewById(chipGroupIds[i]);
            if (group == null) continue;
            for (int j = 0; j < group.getChildCount(); j++) {
                if (((Chip) group.getChildAt(j)).isChecked()) {
                    toggleCategory(i);
                    return; // Only expand the first category found
                }
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
        findViewById(R.id.ivBackEditProfile).setOnClickListener(v -> finish());
        etBirthDate.setOnClickListener(v -> showDatePicker());

        View avatarArea = findViewById(R.id.layoutAvatarSection);
        if (avatarArea != null) avatarArea.setOnClickListener(v -> showAvatarOptions());

        btnSaveProfile.setOnClickListener(v -> {
            String name = etDisplayName.getText() != null
                    ? etDisplayName.getText().toString().trim() : "";
            String birthDate = etBirthDate.getText() != null
                    ? etBirthDate.getText().toString().trim() : "";
            String bio = etBio.getText() != null
                    ? etBio.getText().toString().trim() : "";

            if (name.isEmpty()) {
                etDisplayName.setError("Vui lòng nhập tên");
                return;
            }

            String gender = "";
            int genderId = chipGroupGender.getCheckedChipId();
            if (genderId == R.id.chipMale) gender = "Nam";
            else if (genderId == R.id.chipFemale) gender = "Nữ";
            else if (genderId == R.id.chipOther) gender = "Khác";

            saveToBackend(name, birthDate, bio, gender, selectedInterests);
        });
    }

    // ===== AVATAR OPTIONS =====

    private void showAvatarOptions() {
        boolean hasAvatar = currentAvatarUrl != null && !currentAvatarUrl.isEmpty();
        String[] options = hasAvatar
                ? new String[]{"Xem ảnh đại diện", "Cập nhật ảnh đại diện", "Xóa ảnh đại diện"}
                : new String[]{"Cập nhật ảnh đại diện"};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Ảnh đại diện")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if ("Xem ảnh đại diện".equals(selected)) viewCurrentAvatar();
                    else if ("Cập nhật ảnh đại diện".equals(selected)) pickAvatar();
                    else if ("Xóa ảnh đại diện".equals(selected)) confirmDeleteAvatar();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void viewCurrentAvatar() {
        if (currentAvatarUrl == null || currentAvatarUrl.isEmpty()) return;
        String displayUrl = currentAvatarUrl.startsWith("/")
                ? RetrofitClient.getBaseUrl() + currentAvatarUrl.substring(1) : currentAvatarUrl;

        int sizePx = (int) (260 * getResources().getDisplayMetrics().density);
        ImageView bigAvatar = new ImageView(this);
        bigAvatar.setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));
        bigAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(displayUrl)
                .placeholder(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(bigAvatar);

        LinearLayout container = new LinearLayout(this);
        container.setGravity(Gravity.CENTER);
        container.setPadding(40, 40, 40, 24);
        container.addView(bigAvatar);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(container)
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        imagePicker.launch(intent);
    }

    private void confirmDeleteAvatar() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Xóa ảnh đại diện")
                .setMessage("Bạn có chắc muốn xóa ảnh đại diện hiện tại?")
                .setPositiveButton("Xóa", (dialog, which) -> doDeleteAvatar())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void doDeleteAvatar() {
        RetrofitClient.loadToken(this);
        Map<String, Object> body = new HashMap<>();
        body.put("avatarUrl", "");

        RetrofitClient.getClient().create(UserApiService.class)
                .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.isSuccessful()) {
                            currentAvatarUrl = "";
                            RetrofitClient.saveAvatarUrl(EditProfileActivity.this, "");
                            clearGlideCache();
                            displayAvatar("");
                            Toast.makeText(EditProfileActivity.this,
                                    "Đã xóa ảnh đại diện", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(EditProfileActivity.this,
                                    "Không thể xóa ảnh. Thử lại sau.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(EditProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadAvatar(Uri uri) {
        btnSaveProfile.setEnabled(false);
        Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

        try {
            File file = uriToFile(uri);
            if (file == null) {
                btnSaveProfile.setEnabled(true);
                Toast.makeText(this, "Không thể đọc ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody reqBody = RequestBody.create(MediaType.parse("image/*"), file);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), reqBody);

            RetrofitClient.loadToken(this);
            RetrofitClient.getClient().create(PostApiService.class)
                    .uploadImage(part).enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call,
                                               Response<ApiResponse<String>> response) {
                            btnSaveProfile.setEnabled(true);
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                String url = response.body().getResult();
                                if (url.startsWith("/")) {
                                    url = RetrofitClient.getBaseUrl() + url.substring(1);
                                }
                                final String avatarUrl = url;
                                currentAvatarUrl = avatarUrl;
                                RetrofitClient.saveAvatarUrl(EditProfileActivity.this, avatarUrl);

                                Map<String, Object> body = new HashMap<>();
                                body.put("avatarUrl", avatarUrl);
                                RetrofitClient.getClient().create(UserApiService.class)
                                        .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Map<String, Object>>> c,
                                                                   Response<ApiResponse<Map<String, Object>>> r) {}
                                            @Override
                                            public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {}
                                        });

                                clearGlideCache();
                                displayAvatar(avatarUrl);
                                Toast.makeText(EditProfileActivity.this,
                                        "Ảnh đại diện đã được cập nhật!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(EditProfileActivity.this,
                                        "Không thể tải ảnh lên", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            btnSaveProfile.setEnabled(true);
                            Toast.makeText(EditProfileActivity.this, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            btnSaveProfile.setEnabled(true);
            Toast.makeText(this, "Không thể xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearGlideCache() {
        Glide.get(this).clearMemory();
        new Thread(() -> Glide.get(this).clearDiskCache()).start();
    }

    private File uriToFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File tmp = File.createTempFile("avatar_", ".jpg", getCacheDir());
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) out.write(buf, 0, len);
            out.close();
            is.close();
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ===== SAVE PROFILE =====

    private void saveToBackend(String name, String birthday, String bio,
                               String gender, List<String> interests) {
        RetrofitClient.loadToken(this);
        if (RetrofitClient.getAuthToken() == null) {
            Toast.makeText(this, "Đã lưu thay đổi thành công!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("Đang lưu...");

        Map<String, Object> body = new HashMap<>();
        body.put("fullName", name);
        body.put("birthday", birthday);
        body.put("bio", bio);
        body.put("gender", gender);
        body.put("interestTags", String.join(",", interests));
        // Include current avatar URL so it is not cleared server-side
        body.put("avatarUrl", currentAvatarUrl != null ? currentAvatarUrl : "");

        RetrofitClient.getClient().create(UserApiService.class)
                .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        btnSaveProfile.setEnabled(true);
                        btnSaveProfile.setText("Lưu thay đổi");
                        if (response.isSuccessful()) {
                            RetrofitClient.saveUserName(EditProfileActivity.this, name);
                            getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("user_interests", String.join(",", interests))
                                    .apply();

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
                        btnSaveProfile.setEnabled(true);
                        btnSaveProfile.setText("Lưu thay đổi");
                        Toast.makeText(EditProfileActivity.this,
                                "Lỗi kết nối. Thử lại sau.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ===== DATE PICKER =====

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR) - 20;
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog picker = new DatePickerDialog(
                this,
                (view, selectedYear, selectedMonth, selectedDay) -> {
                    String date = String.format(Locale.getDefault(), "%02d/%02d/%04d",
                            selectedDay, selectedMonth + 1, selectedYear);
                    etBirthDate.setText(date);
                },
                year, month, day
        );
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        picker.show();
    }
}
