package com.example.weconnect.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.utils.InterestTextUtils;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OnboardingActivity extends AppCompatActivity {

    private static final int MAX_SELECTIONS = 5;

    private final List<String> selectedInterests = new ArrayList<>();
    private TextView tvSelectedCount;
    private FloatingActionButton fabNext;

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
                "☕ Đi Cafe cà pháo", "🍜 Đi Foodtour / Ăn sập phố cổ", "🍽️ Food Tour",
                "✈️ Đi du lịch xa / Phượt", "🏕️ Đi cắm trại / Camping", "🌿 Đi dạo công viên / Picnic",
                "🧗 Leo núi tự nhiên / Trekking", "🐕 Đi offline giao lưu thú cưng",
                "🎪 Làm tình nguyện / Từ thiện", "🛍️ Đi mua sắm / Shopping", "🎣 Đi câu cá thư giãn"
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        fabNext = findViewById(R.id.fabNext);
        setupChipGroups();

        fabNext.setOnClickListener(v -> {
            if (selectedInterests.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn ít nhất 1 sở thích", Toast.LENGTH_SHORT).show();
                return;
            }
            // Save to FakePostRepository (fallback)
            FakePostRepository.getInstance().setUserInterests(selectedInterests);
            // Save to SharedPreferences (cho CreatePost dùng offline)
            saveInterestsToPrefs(selectedInterests);
            // Save to backend API
            saveInterestsToBackend(selectedInterests);
        });

        updateCounter();
    }

    private void setupChipGroups() {
        int[] chipGroupIds = {
                R.id.chipGroupSport,
                R.id.chipGroupEntertainment,
                R.id.chipGroupCreative,
                R.id.chipGroupStudy,
                R.id.chipGroupSocial,
                R.id.chipGroupTrend
        };
        String[] keys = categories.keySet().toArray(new String[0]);

        for (int i = 0; i < chipGroupIds.length; i++) {
            ChipGroup chipGroup = findViewById(chipGroupIds[i]);
            String[] items = categories.get(keys[i]);
            if (items == null) continue;

            for (String item : items) {
                String label = InterestTextUtils.stripLeadingIcon(item);
                Chip chip = new Chip(this);
                chip.setText(label);
                chip.setTag(item);
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
                                getResources().getColor(R.color.card_surface, null)));
                        chip.setTextColor(getResources().getColor(R.color.text_primary, null));
                        chip.setChipStrokeWidth(2f);
                    }
                    updateCounter();
                });

                chipGroup.addView(chip);
            }
        }
    }

    private void updateCounter() {
        tvSelectedCount.setText(selectedInterests.size() + "/" + MAX_SELECTIONS + " đã chọn");

        if (selectedInterests.isEmpty()) {
            fabNext.setAlpha(0.5f);
        } else {
            fabNext.setAlpha(1.0f);
        }
    }

    private void saveInterestsToPrefs(List<String> interests) {
        android.content.SharedPreferences prefs =
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        prefs.edit().putString("user_interests", String.join(",", interests)).apply();
    }

    private void saveInterestsToBackend(List<String> interests) {
        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            // Chưa đăng nhập → chỉ lưu local, vẫn đi tiếp
            goToMain();
            return;
        }

        UserApiService apiService = RetrofitClient.getClient().create(UserApiService.class);
        Map<String, List<String>> body = new HashMap<>();
        body.put("interests", interests);

        apiService.saveInterests(body).enqueue(new Callback<ApiResponse<List<String>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<String>>> call,
                                   Response<ApiResponse<List<String>>> response) {
                goToMain();
            }

            @Override
            public void onFailure(Call<ApiResponse<List<String>>> call, Throwable t) {
                // API thất bại vẫn đi tiếp (đã lưu local)
                goToMain();
            }
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
