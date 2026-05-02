package com.example.weconnect.activities;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.api.FirestoreUserRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

public class BlockedUsersActivity extends AppCompatActivity {

    private LinearLayout contentContainer;
    private FirestoreUserRepository userRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        userRepo = new FirestoreUserRepository();

        androidx.constraintlayout.widget.ConstraintLayout root =
            new androidx.constraintlayout.widget.ConstraintLayout(this);
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
        ivBack.setLayoutParams(new LinearLayout.LayoutParams(96, 96));
        header.addView(ivBack);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Danh sách chặn");
        tvTitle.setTextSize(20);
        tvTitle.setTextColor(getResources().getColor(R.color.primary_pink, null));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setPadding(24, 0, 0, 0);
        header.addView(tvTitle);
        root.addView(header);

        // Content
        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(48, 0, 48, 48);
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
            new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT);
        lp.topToBottom = header.getId();
        lp.topMargin = 16;
        contentContainer.setLayoutParams(lp);
        root.addView(contentContainer);

        loadBlockedUsers();
    }

    private void loadBlockedUsers() {
        contentContainer.removeAllViews();

        TextView loading = new TextView(this);
        loading.setText("Đang tải...");
        loading.setTextSize(15);
        loading.setTextColor(getResources().getColor(R.color.text_secondary, null));
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(0, 120, 0, 0);
        contentContainer.addView(loading);

        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) { showEmptyState(); return; }

        userRepo.getBlockedUsers(uid, new FirestoreUserRepository.UsersCallback() {
            @Override public void onSuccess(List<Map<String, Object>> blocked) {
                runOnUiThread(() -> {
                    contentContainer.removeAllViews();
                    if (blocked.isEmpty()) { showEmptyState(); return; }
                    for (Map<String, Object> u : blocked) {
                        String name  = u.get("fullName") != null ? u.get("fullName").toString() : "Người dùng";
                        String bUid  = u.get("id") != null ? u.get("id").toString() : "";
                        addBlockedUserRow(name, bUid);
                    }
                });
            }
            @Override public void onError(String err) {
                runOnUiThread(() -> {
                    contentContainer.removeAllViews();
                    TextView tv = new TextView(BlockedUsersActivity.this);
                    tv.setText("Lỗi kết nối. Vui lòng thử lại.");
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(0, 120, 0, 0);
                    contentContainer.addView(tv);
                });
            }
        });
    }

    private void showEmptyState() {
        TextView tv = new TextView(this);
        tv.setText("Bạn chưa chặn ai");
        tv.setTextSize(15);
        tv.setTextColor(getResources().getColor(R.color.text_secondary, null));
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 120, 0, 0);
        contentContainer.addView(tv);
    }

    private void addBlockedUserRow(String name, String blockedUid) {
        com.google.android.material.card.MaterialCardView card =
            new com.google.android.material.card.MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = 24;
        card.setLayoutParams(cardLp);
        card.setCardBackgroundColor(getResources().getColor(R.color.card_surface, null));
        card.setRadius(48f);
        card.setCardElevation(6f);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(36, 28, 36, 28);

        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_user_placeholder);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(96, 96));
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
        btnUnblock.setInsetTop(0); btnUnblock.setInsetBottom(0);
        btnUnblock.setMinWidth(0); btnUnblock.setMinimumWidth(0);
        btnUnblock.setPadding(40, 0, 40, 0);
        btnUnblock.setOnClickListener(v -> {
            String myUid = FirebaseManager.getCurrentUserId();
            if (myUid == null || blockedUid.isEmpty()) {
                Toast.makeText(this, "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                return;
            }
            btnUnblock.setEnabled(false);
            btnUnblock.setText("...");
            userRepo.unblockUser(myUid, blockedUid, new FirestoreUserRepository.ActionCallback() {
                @Override public void onSuccess(String msg) {
                    runOnUiThread(() -> {
                        Toast.makeText(BlockedUsersActivity.this, "Đã bỏ chặn " + name, Toast.LENGTH_SHORT).show();
                        loadBlockedUsers();
                    });
                }
                @Override public void onError(String err) {
                    runOnUiThread(() -> {
                        Toast.makeText(BlockedUsersActivity.this, "Không thể bỏ chặn", Toast.LENGTH_SHORT).show();
                        btnUnblock.setEnabled(true);
                        btnUnblock.setText("Bỏ chặn");
                    });
                }
            });
        });
        row.addView(btnUnblock);
        card.addView(row);
        contentContainer.addView(card);
    }
}
