package com.example.weconnect.social.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.social.data.FriendApiService;
import com.example.weconnect.core.data.RetrofitClient;
import com.example.weconnect.core.data.ApiResponse;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BlockedUsersActivity extends AppCompatActivity {

    private LinearLayout contentContainer;
    private FriendApiService friendApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        androidx.constraintlayout.widget.ConstraintLayout root = new androidx.constraintlayout.widget.ConstraintLayout(this);
        root.setBackgroundColor(getResources().getColor(R.color.soft_beige, null));
        root.setFitsSystemWindows(true);
        setContentView(root);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setId(android.R.id.content + 100);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(48, 48, 48, 32);

        ImageView ivBack = new ImageView(this);
        ivBack.setImageResource(R.drawable.ic_close);
        ivBack.setPadding(24, 24, 24, 24);
        ivBack.setOnClickListener(v -> finish());
        ivBack.setColorFilter(getResources().getColor(R.color.primary_pink, null));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(96, 96);
        ivBack.setLayoutParams(backLp);
        header.addView(ivBack);

        TextView title = new TextView(this);
        title.setText("Danh sách chặn");
        title.setTextSize(20);
        title.setTextColor(getResources().getColor(R.color.primary_pink, null));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 0, 0, 0);
        header.addView(title);

        root.addView(header);

        // Content
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(48, 0, 48, 48);
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams contentLp =
                new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
        contentLp.topToBottom = header.getId();
        contentLp.topMargin = 16;
        contentContainer.setLayoutParams(contentLp);
        root.addView(contentContainer);

        // Init API
        RetrofitClient.loadToken(this);
        friendApi = RetrofitClient.getClient().create(FriendApiService.class);

        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        contentContainer.removeAllViews();

        // Loading state
        TextView loading = new TextView(this);
        loading.setText("Đang tải...");
        loading.setTextSize(15);
        loading.setTextColor(getResources().getColor(R.color.text_secondary, null));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 120, 0, 0);
        contentContainer.addView(loading);

        friendApi.getBlockedUsers().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                contentContainer.removeAllViews();

                if (!response.isSuccessful() || response.body() == null
                        || response.body().getResult() == null
                        || response.body().getResult().isEmpty()) {
                    showEmptyState();
                    return;
                }

                List<Map<String, Object>> blockedList = response.body().getResult();
                for (Map<String, Object> blocked : blockedList) {
                    String name = blocked.get("fullName") != null
                            ? blocked.get("fullName").toString() : "Người dùng";
                    long userId = -1;
                    try {
                        if (blocked.get("id") != null)
                            userId = ((Number) blocked.get("id")).longValue();
                    } catch (Exception ignored) {}

                    addBlockedUserRow(name, userId);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                contentContainer.removeAllViews();
                TextView error = new TextView(BlockedUsersActivity.this);
                error.setText("Lỗi kết nối. Vui lòng thử lại.");
                error.setTextSize(15);
                error.setTextColor(getResources().getColor(R.color.text_secondary, null));
                error.setGravity(Gravity.CENTER);
                error.setPadding(0, 120, 0, 0);
                contentContainer.addView(error);
            }
        });
    }

    private void showEmptyState() {
        TextView empty = new TextView(this);
        empty.setText("Bạn chưa chặn ai");
        empty.setTextSize(15);
        empty.setTextColor(getResources().getColor(R.color.text_secondary, null));
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, 120, 0, 0);
        contentContainer.addView(empty);
    }

    private void addBlockedUserRow(String name, long userId) {
        com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = 24;
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(getResources().getColor(R.color.card_surface, null));
        card.setRadius(48f);
        card.setCardElevation(6f);
        card.setStrokeWidth(0);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(36, 28, 36, 28);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_user_placeholder);
        LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(96, 96);
        avatar.setLayoutParams(avatarLp);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        row.addView(avatar);

        TextView tvName = new TextView(this);
        tvName.setText(name);
        tvName.setTextSize(15);
        tvName.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        tvName.setPadding(32, 0, 0, 0);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tvName.setLayoutParams(nameLp);
        row.addView(tvName);

        MaterialButton btnUnblock = new MaterialButton(this);
        btnUnblock.setText("Bỏ chặn");
        btnUnblock.setAllCaps(false);
        btnUnblock.setTextSize(12);
        btnUnblock.setCornerRadius(60);
        btnUnblock.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF4D6D));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, 84);
        btnUnblock.setLayoutParams(btnLp);
        btnUnblock.setInsetTop(0);
        btnUnblock.setInsetBottom(0);
        btnUnblock.setMinWidth(0);
        btnUnblock.setMinimumWidth(0);
        btnUnblock.setPadding(40, 0, 40, 0);
        btnUnblock.setOnClickListener(v -> {
            if (userId <= 0) {
                Toast.makeText(this, "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                return;
            }
            btnUnblock.setEnabled(false);
            btnUnblock.setText("...");
            friendApi.unblockUser(userId).enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(BlockedUsersActivity.this,
                                "Đã bỏ chặn " + name, Toast.LENGTH_SHORT).show();
                        loadBlockedUsers(); // Refresh list
                    } else {
                        Toast.makeText(BlockedUsersActivity.this,
                                "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                        btnUnblock.setEnabled(true);
                        btnUnblock.setText("Bỏ chặn");
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    Toast.makeText(BlockedUsersActivity.this,
                            "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    btnUnblock.setEnabled(true);
                    btnUnblock.setText("Bỏ chặn");
                }
            });
        });
        row.addView(btnUnblock);

        card.addView(row);
        contentContainer.addView(card);
    }
}
