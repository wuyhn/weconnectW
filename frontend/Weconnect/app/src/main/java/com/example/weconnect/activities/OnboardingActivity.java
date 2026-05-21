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

    // Categories with emoji + label
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
                R.id.chipGroupSocial
        };
        String[] keys = categories.keySet().toArray(new String[0]);

        for (int i = 0; i < chipGroupIds.length; i++) {
            ChipGroup chipGroup = findViewById(chipGroupIds[i]);
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
