package com.example.weconnect.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.example.weconnect.data.AdministrativeLocationData;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.utils.InterestTextUtils;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.Normalizer;
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
    private TextInputLayout tilProvince;
    private TextInputEditText etDisplayName, etBirthDate, etProvince, etBio;
    private ChipGroup chipGroupGender;
    private MaterialButton btnSaveProfile;
    private TextView tvInterestCount;

    private static final int MAX_SELECTIONS = 5;
    private final List<String> selectedInterests = new ArrayList<>();
    private String currentAvatarUrl = "";
    private String selectedProvinceId = "";
    private String selectedProvinceName = "";

    // 60 tag chính thức WeConnect — BẮT BUỘC đồng bộ với GeminiService.SYSTEM_TAGS ở Backend
    private final Map<String, String[]> categories = new LinkedHashMap<String, String[]>() {{
        // Nhóm 1: Thể thao (12 tags)
        put("sport", new String[]{
                "⚽ Đá bóng sân cỏ", "🏸 Đánh cầu lông", "🏀 Đánh bóng rổ", "🏐 Đánh bóng chuyền",
                "🏃 Chạy bộ công viên", "🚴 Đạp xe đường phố", "🏊 Đi bơi hồ", "🎾 Đánh Pickleball",
                "🛹 Trượt ván / Patin", "🧗 Leo núi nhân tạo", "🧘 Tập Yoga / Pilates", "🏋️ Tập Gym / Calisthenics"
        });
        // Nhóm 2: Giải trí (10 tags)
        put("entertainment", new String[]{
                "🎬 Xem phim rạp", "🎵 Đi nghe nhạc / Concert", "🎤 Đi hát Karaoke",
                "🎮 Chơi Game (PC/Console)", "📱 Chơi Mobile Game / Liên Quân", "🎲 Chơi Board game / Ma soi",
                "📸 Đi chụp ảnh / Check-in", "🎨 Vẽ tranh thư giãn", "💃 Học nhảy / Vũ đạo",
                "🎭 Xem kịch / Xem Stand-up Comedy"
        });
        // Nhóm 3: Học tập / Công nghệ (10 tags)
        put("tech", new String[]{
                "☕ Học nhóm / Chạy deadline", "📖 Đọc sách tại thư viện", "🌍 Luyện nói tiếng Anh",
                "🎌 Học tiếng Nhật / Trung / Hàn", "💻 Lập trình dự án / Hackathon",
                "📐 Thiết kế đồ họa / UI-UX", "📝 Ôn thi / Giải đề",
                "💼 Thảo luận ý tưởng khởi nghiệp", "🔬 Làm thí nghiệm / Nghiên cứu",
                "🤖 Lập trình AI / Học Data Science"
        });
        // Nhóm 4: Đời sống / Du lịch (10 tags)
        put("lifestyle", new String[]{
                "☕ Đi Cafe cà pháo", "🍜 Đi Foodtour / Ăn sập phố cổ", "✈️ Đi du lịch xa / Phượt",
                "🏕️ Đi cắm trại / Camping", "🌿 Đi dạo công viên / Picnic", "🧗 Leo núi tự nhiên / Trekking",
                "🐕 Đi offline giao lưu thú cưng", "🎪 Làm tình nguyện / Từ thiện",
                "🛍️ Đi mua sắm / Shopping", "🎣 Đi câu cá thư giãn"
        });
        // Nhóm 5: Giao lưu / Hobby (10 tags)
        put("social", new String[]{
                "💬 Trò chuyện tâm sự / Hướng nội", "🍻 Nhậu nhẹt / Chill cuối tuần",
                "🎸 Tập chơi nhạc cụ (Guitar/Piano)", "🧩 Xếp hình Lego / Giải Rubik",
                "✍️ Viết lách / Viết Blog", "🎬 Quay Video / Làm Tiktok",
                "🔮 Xem bài Tarot / Chiêm tinh", "🍳 Tụ tập nấu ăn / Làm bánh",
                "🪴 Trồng cây / Làm vườn", "🪡 Thêu thùa / Làm đồ thủ công"
        });
        // Nhóm 6: Xu hướng & Kỹ năng (8 tags)
        put("trend", new String[]{
                "♟️ Đánh cờ vua / Cờ tướng", "🎤 Tập nói trước đám đông / Debate",
                "💸 Học quản lý tài chính cá nhân", "🚗 Tập lái xe / Trải nghiệm xe",
                "🎯 Chơi bắn cung / Phi tiêu", "🎳 Chơi Bowling",
                "🎈 Tham gia lễ hội / Fandom", "🧩 Đi giải mật phòng (Escape Room)"
        });
    }};

    private final int[] chipGroupIds = {
            R.id.chipGroupSport, R.id.chipGroupEntertainment,
            R.id.chipGroupTech, R.id.chipGroupLifestyle,
            R.id.chipGroupSocial, R.id.chipGroupTrend
    };

    private final int[] categoryRowIds = {
            R.id.rowCategorySport, R.id.rowCategoryEntertainment,
            R.id.rowCategoryTech, R.id.rowCategoryLifestyle,
            R.id.rowCategorySocial, R.id.rowCategoryTrend
    };

    private final int[] chevronIds = {
            R.id.ivChevronSport, R.id.ivChevronEntertainment,
            R.id.ivChevronTech, R.id.ivChevronLifestyle,
            R.id.ivChevronSocial, R.id.ivChevronTrend
    };

    private final boolean[] categoryExpanded = {false, false, false, false, false, false};

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
        etProvince = findViewById(R.id.etProvince);
        etBio = findViewById(R.id.etBio);
        tilProvince = findViewById(R.id.tilProvince);
        chipGroupGender = findViewById(R.id.chipGroupGender);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        tvInterestCount = findViewById(R.id.tvInterestCount);

        currentAvatarUrl = RetrofitClient.getAvatarUrl(this);
        displayAvatar(currentAvatarUrl);
    }

    private String resolveAvatarUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        if (url.startsWith("/")) {
            return RetrofitClient.getBaseUrl() + url.substring(1);
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                java.net.URL parsed = new java.net.URL(url);
                String path = parsed.getPath();
                if (path != null && path.startsWith("/uploads/")) {
                    return RetrofitClient.getBaseUrl() + path.substring(1);
                }
            } catch (Exception ignored) {}
        }
        return url;
    }

    private void displayAvatar(String url) {
        if (ivEditProfileAvatar == null) return;
        if (url != null && !url.isEmpty()) {
            String displayUrl = resolveAvatarUrl(url);
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
                String label = InterestTextUtils.stripLeadingIcon(item);
                Chip chip = new Chip(this);
                chip.setText(label);
                chip.setTag(item);
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
                    Object rawTag = chip.getTag();
                    String tag = rawTag != null ? rawTag.toString() : chip.getText().toString();
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
                    String provinceId = profile.get("provinceId") != null
                            ? profile.get("provinceId").toString() : "";
                    String provinceName = profile.get("provinceName") != null
                            ? profile.get("provinceName").toString() : "";
                    String interestTags = profile.get("interestTags") != null
                            ? profile.get("interestTags").toString() : "";
                    String avatarUrl = profile.get("avatarUrl") != null
                            ? profile.get("avatarUrl").toString() : "";

                    etDisplayName.setText(fullName);
                    etBirthDate.setText(formatBirthdayForDisplay(birthday));
                    selectedProvinceId = provinceId;
                    selectedProvinceName = provinceName;
                    etProvince.setText(provinceName);
                    RetrofitClient.saveUserProvince(EditProfileActivity.this, provinceId, provinceName);
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
                Object rawTag = chip.getTag();
                String candidate = rawTag != null ? rawTag.toString() : chip.getText().toString();
                if (InterestTextUtils.stripLeadingIcon(candidate)
                        .equals(InterestTextUtils.stripLeadingIcon(text))) {
                    chip.setChecked(true);
                    return;
                }
            }
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.ivBackEditProfile).setOnClickListener(v -> finish());
        etBirthDate.setOnClickListener(v -> showDatePicker());
        etProvince.setOnClickListener(v -> showProvinceBottomSheet());
        if (tilProvince != null) {
            tilProvince.setOnClickListener(v -> showProvinceBottomSheet());
            tilProvince.setEndIconOnClickListener(v -> showProvinceBottomSheet());
        }

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

            saveToBackend(name, birthDate, bio, gender, selectedProvinceId, selectedProvinceName, selectedInterests);
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

            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "image/jpeg";
            if ("image/jpg".equalsIgnoreCase(mimeType)) mimeType = "image/jpeg";
            String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
            String uploadName = "avatar_" + System.currentTimeMillis() + ext;

            RequestBody reqBody = RequestBody.create(MediaType.parse(mimeType), file);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", uploadName, reqBody);

            RetrofitClient.loadToken(this);
            RetrofitClient.getClient().create(PostApiService.class)
                    .uploadImage(part).enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call,
                                               Response<ApiResponse<String>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                final String avatarUrl = response.body().getResult();

                                Map<String, Object> body = new HashMap<>();
                                body.put("avatarUrl", avatarUrl);
                                RetrofitClient.getClient().create(UserApiService.class)
                                        .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Map<String, Object>>> c,
                                                                   Response<ApiResponse<Map<String, Object>>> r) {
                                                btnSaveProfile.setEnabled(true);
                                                if (r.isSuccessful()) {
                                                    currentAvatarUrl = avatarUrl;
                                                    RetrofitClient.saveAvatarUrl(EditProfileActivity.this, avatarUrl);
                                                    clearGlideCache();
                                                    displayAvatar(avatarUrl);
                                                    setResult(RESULT_OK);
                                                    Toast.makeText(EditProfileActivity.this,
                                                            "Ảnh đại diện đã được cập nhật!", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(EditProfileActivity.this,
                                                            "Không thể cập nhật hồ sơ với ảnh mới", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                            @Override
                                            public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {
                                                btnSaveProfile.setEnabled(true);
                                                Toast.makeText(EditProfileActivity.this,
                                                        "Lỗi kết nối khi cập nhật hồ sơ", Toast.LENGTH_SHORT).show();
                                            }
                                        });

                            } else {
                                btnSaveProfile.setEnabled(true);
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
                               String gender, String provinceId, String provinceName, List<String> interests) {
        RetrofitClient.loadToken(this);
        if (RetrofitClient.getAuthToken() == null) {
            RetrofitClient.saveUserName(this, name);
            RetrofitClient.saveUserProvince(this, provinceId, provinceName);
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
        body.put("provinceId", provinceId != null ? provinceId : "");
        body.put("provinceName", provinceName != null ? provinceName : "");
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
                            RetrofitClient.saveUserProvince(EditProfileActivity.this, provinceId, provinceName);
                            getSharedPreferences("weconnect_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("user_interests", String.join(",", interests))
                                    .apply();

                            Toast.makeText(EditProfileActivity.this,
                                    "Đã lưu thay đổi thành công!", Toast.LENGTH_SHORT).show();
                            Intent result = new Intent();
                            result.putExtra("provinceId", provinceId);
                            result.putExtra("provinceName", provinceName);
                            result.putExtra("fullName", name);
                            setResult(RESULT_OK, result);
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

    // ===== PROVINCE PICKER =====

    private void showProvinceBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpPx(20), dpPx(10), dpPx(20), dpPx(12));
        root.setBackgroundColor(0x00000000);

        View dragHandle = new View(this);
        dragHandle.setBackground(createRoundedDrawable(0xFFD4CEC5, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dpPx(44), dpPx(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dpPx(18);
        root.addView(dragHandle, handleParams);

        TextView title = new TextView(this);
        title.setText("Chọn địa điểm");
        title.setTextColor(0xFF1A1208);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        EditText searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Tìm tỉnh/thành phố");
        searchInput.setHintTextColor(0xFF9A9082);
        searchInput.setTextColor(0xFF1A1208);
        searchInput.setTextSize(15);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setPadding(dpPx(14), 0, dpPx(14), 0);
        searchInput.setBackground(createRoundedDrawable(0xFFF4F1EC, 14));

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

            int targetHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.72f);
            ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
            params.height = targetHeight;
            bottomSheet.setLayoutParams(params);
            bottomSheet.setBackground(createTopRoundedDrawable(0xFFFFFFFF, 28));

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

        int accentColor = getResources().getColor(R.color.primary_pink, null);
        for (AdministrativeLocationData.Province province : filtered) {
            boolean selected = province.id.equals(selectedProvinceId)
                    || province.name.equalsIgnoreCase(selectedProvinceName);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpPx(4), 0, dpPx(4), 0);
            row.setMinimumHeight(dpPx(52));
            row.setBackgroundResource(selectableItemBackground());

            TextView nameView = new TextView(this);
            nameView.setText(province.name);
            nameView.setTextColor(selected ? accentColor : 0xFF1A1208);
            nameView.setTextSize(16);
            nameView.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            row.addView(nameView, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            ImageView checkIcon = new ImageView(this);
            checkIcon.setImageResource(R.drawable.ic_check);
            checkIcon.setColorFilter(accentColor);
            checkIcon.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            row.addView(checkIcon, new LinearLayout.LayoutParams(dpPx(22), dpPx(22)));

            row.setOnClickListener(v -> {
                selectedProvinceId = province.id;
                selectedProvinceName = province.name;
                etProvince.setText(province.name);
                dialog.dismiss();
            });

            container.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpPx(52)
            ));
        }
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

    private GradientDrawable createTopRoundedDrawable(int color, int radiusDp) {
        float radius = dpPx(radiusDp);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        return drawable;
    }

    private int selectableItemBackground() {
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId;
    }

    private int dpPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    interface OnChanged { void changed(CharSequence s); }

    static class SimpleWatcher implements TextWatcher {
        private final OnChanged listener;

        SimpleWatcher(OnChanged listener) {
            this.listener = listener;
        }

        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            listener.changed(s);
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void afterTextChanged(Editable s) {}
    }

    // ===== DATE PICKER =====

    private void showDatePicker() {
        Calendar calendar = parseBirthdayCalendar(
                etBirthDate.getText() != null ? etBirthDate.getText().toString() : null);
        if (calendar == null) {
            calendar = Calendar.getInstance();
            calendar.add(Calendar.YEAR, -20);
        }

        int year = calendar.get(Calendar.YEAR);
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

    private String formatBirthdayForDisplay(String rawBirthday) {
        Calendar parsed = parseBirthdayCalendar(rawBirthday);
        if (parsed == null) return rawBirthday != null ? rawBirthday : "";
        return String.format(Locale.getDefault(), "%02d/%02d/%04d",
                parsed.get(Calendar.DAY_OF_MONTH),
                parsed.get(Calendar.MONTH) + 1,
                parsed.get(Calendar.YEAR));
    }

    private Calendar parseBirthdayCalendar(String rawBirthday) {
        if (rawBirthday == null) return null;
        String value = rawBirthday.trim();
        if (value.isEmpty()) return null;
        int timeIndex = value.indexOf('T');
        if (timeIndex > 0) value = value.substring(0, timeIndex);

        try {
            int day;
            int month;
            int year;
            if (value.contains("/")) {
                String[] parts = value.split("/");
                if (parts.length != 3) return null;
                if (parts[0].length() == 4) {
                    year = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]);
                    day = Integer.parseInt(parts[2]);
                } else {
                    day = Integer.parseInt(parts[0]);
                    month = Integer.parseInt(parts[1]);
                    year = Integer.parseInt(parts[2]);
                }
            } else if (value.contains("-")) {
                String[] parts = value.split("-");
                if (parts.length != 3) return null;
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
                day = Integer.parseInt(parts[2]);
            } else {
                return null;
            }

            Calendar parsed = Calendar.getInstance();
            parsed.setLenient(false);
            parsed.set(year, month - 1, day, 0, 0, 0);
            parsed.set(Calendar.MILLISECOND, 0);
            parsed.getTime();
            return parsed;
        } catch (Exception ignored) {
            return null;
        }
    }
}
