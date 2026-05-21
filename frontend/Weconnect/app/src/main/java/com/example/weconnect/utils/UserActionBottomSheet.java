package com.example.weconnect.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserActionBottomSheet {

    private static final String STATUS_FRIEND = "FRIEND";
    private static final String STATUS_PENDING_SENT = "PENDING_SENT";
    private static final String STATUS_PENDING_RECEIVED = "PENDING_RECEIVED";

    // Colors
    private static final int COLOR_SURFACE    = 0xFFFFFFFF;
    private static final int COLOR_SEPARATOR  = 0xFFD1D1D6;
    private static final int COLOR_LABEL_DIM  = 0xFF8E8E93; // name header, muted
    private static final int COLOR_TEXT       = 0xFF1C1C1E; // black
    private static final int COLOR_DESTRUCTIVE= 0xFFFF3B30; // red — block
    private static final int COLOR_BADGE_GREEN= 0xFF34C759;
    private static final int COLOR_BADGE_ORG  = 0xFFFF9500;
    private static final int COLOR_BADGE_BLUE = 0xFF007AFF;

    public static void show(Context context, long userId, String displayName, String avatarUrl) {
        long myId = RetrofitClient.getUserId(context);
        if (userId > 0 && userId == myId) return;
        if (userId <= 0) {
            showInternal(context, userId, displayName, avatarUrl, false, false);
            return;
        }

        RetrofitClient.loadToken(context);
        RetrofitClient.getClient().create(FriendApiService.class)
                .getBlockStatus(userId).enqueue(new Callback<ApiResponse<java.util.Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<java.util.Map<String, Object>>> call,
                                           Response<ApiResponse<java.util.Map<String, Object>>> response) {
                        java.util.Map<String, Object> result = response.isSuccessful()
                                && response.body() != null ? response.body().getResult() : null;
                        showInternal(context, userId, displayName, avatarUrl,
                                asBoolean(result, "isBlockedByMe"),
                                asBoolean(result, "hasBlockedMe"));
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<java.util.Map<String, Object>>> call, Throwable t) {
                        showInternal(context, userId, displayName, avatarUrl, false, false);
                    }
                });
    }

    private static void showInternal(Context context, long userId, String displayName,
                                     String avatarUrl, boolean isBlockedByMe,
                                     boolean hasBlockedMe) {
        long myId = RetrofitClient.getUserId(context);
        if (userId > 0 && userId == myId) return;

        BottomSheetDialog sheet = new BottomSheetDialog(context);
        sheet.getBehavior().setSkipCollapsed(true);

        // Outer wrapper — transparent so we can see dimmed background between groups
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0x00000000);
        int outerH = dpToPx(context, 10);
        root.setPadding(outerH, 0, outerH, outerH);

        // ─── Group 1: Name + dynamic action + profile + block ───
        LinearLayout group1 = roundedGroup(context);

        // Name header (small, centered, gray)
        TextView tvName = new TextView(context);
        tvName.setText(displayName != null ? displayName : "");
        tvName.setTextSize(13);
        tvName.setTextColor(COLOR_LABEL_DIM);
        tvName.setGravity(Gravity.CENTER);
        tvName.setPadding(dpToPx(context, 16), dpToPx(context, 13),
                dpToPx(context, 16), dpToPx(context, 4));
        group1.addView(tvName, matchWidth());

        // Status badge (hidden until API returns)
        TextView tvBadge = new TextView(context);
        tvBadge.setVisibility(View.GONE);
        tvBadge.setGravity(Gravity.CENTER);
        tvBadge.setTextSize(11);
        LinearLayout.LayoutParams badgeLp = matchWidth();
        badgeLp.bottomMargin = dpToPx(context, 6);
        group1.addView(tvBadge, badgeLp);

        // Separator between header and rows
        addSeparator(context, group1);

        // Dynamic row container (Kết bạn / Nhắn tin / etc.)
        LinearLayout dynamicContainer = new LinearLayout(context);
        dynamicContainer.setOrientation(LinearLayout.VERTICAL);
        group1.addView(dynamicContainer, matchWidth());

        // Separator below dynamic (hidden until content added)
        View dynSep = hairline(context);
        dynSep.setVisibility(View.GONE);
        group1.addView(dynSep);

        // "Nhắn tin" — không phụ thuộc friendship status
        addRow(context, group1, "Nhắn tin", COLOR_TEXT, v -> {
            sheet.dismiss();
            DirectMessageHelper.openDirectMessage(context, userId, displayName);
        });

        addSeparator(context, group1);

        // "Xem trang cá nhân"
        addRow(context, group1, "Xem trang cá nhân", COLOR_TEXT, v -> {
            sheet.dismiss();
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", displayName);
            intent.putExtra("view_other", true);
            if (userId > 0) intent.putExtra("user_id", userId);
            if (isBlockedByMe || hasBlockedMe) {
                intent.putExtra("blocked_profile", true);
                intent.putExtra("is_blocked_by_me", isBlockedByMe);
                intent.putExtra("has_blocked_me", hasBlockedMe);
            }
            context.startActivity(intent);
        });

        addSeparator(context, group1);

        addRow(context, group1, "Báo cáo người dùng", COLOR_TEXT, v -> {
            sheet.dismiss();
            UserReportBottomSheet.show(context, userId, displayName);
        });

        addSeparator(context, group1);

        // "Chặn người dùng"
        addRow(context, group1, "Chặn người dùng", COLOR_DESTRUCTIVE, v -> {
            sheet.dismiss();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Chặn " + displayName + "?")
                    .setMessage("Người này sẽ không thể nhắn tin hoặc xem hồ sơ của bạn.")
                    .setPositiveButton("Chặn", (d, w) -> {
                        RetrofitClient.loadToken(context);
                        RetrofitClient.getClient().create(FriendApiService.class)
                                .blockUser(userId).enqueue(new Callback<ApiResponse<Void>>() {
                                    @Override
                                    public void onResponse(Call<ApiResponse<Void>> call,
                                                           Response<ApiResponse<Void>> response) {
                                        Toast.makeText(context, "Đã chặn " + displayName,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    @Override
                                    public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                        Toast.makeText(context, "Lỗi kết nối",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    })
                    .setNegativeButton("Huỷ", null)
                    .show();
        });

        if (isBlockedByMe && group1.getChildCount() > 0
                && group1.getChildAt(group1.getChildCount() - 1) instanceof TextView) {
            TextView blockRow = (TextView) group1.getChildAt(group1.getChildCount() - 1);
            blockRow.setText("Bỏ chặn người dùng");
            blockRow.setTextColor(COLOR_TEXT);
            blockRow.setOnClickListener(v -> {
                sheet.dismiss();
                RetrofitClient.loadToken(context);
                RetrofitClient.getClient().create(FriendApiService.class)
                        .unblockUser(userId).enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(Call<ApiResponse<Void>> call,
                                                   Response<ApiResponse<Void>> response) {
                                Toast.makeText(context,
                                        response.isSuccessful() ? "Đã bỏ chặn" : "Không thể bỏ chặn",
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                            }
                        });
            });
        }

        root.addView(group1, matchWidth());

        // 8dp gap between groups
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(context, 8)));
        root.addView(gap);

        // ─── Group 2: Huỷ ───
        LinearLayout group2 = roundedGroup(context);
        addRow(context, group2, "Huỷ", COLOR_TEXT, v -> sheet.dismiss());
        root.addView(group2, matchWidth());

        // Make dialog container transparent so rounded groups show correctly
        sheet.setContentView(root);
        root.post(() -> {
            if (root.getParent() instanceof View) {
                ((View) root.getParent()).setBackgroundColor(0x00000000);
            }
        });

        // Load friend status
        if (userId > 0) {
            RetrofitClient.loadToken(context);
            RetrofitClient.getClient().create(FriendApiService.class)
                    .getFriendStatus(userId)
                    .enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call,
                                               Response<ApiResponse<String>> response) {
                            String status = (response.isSuccessful()
                                    && response.body() != null
                                    && response.body().getResult() != null)
                                    ? response.body().getResult() : "NONE";
                            fillDynamic(context, dynamicContainer, tvBadge, dynSep,
                                    userId, displayName, status, sheet);
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            fillDynamic(context, dynamicContainer, tvBadge, dynSep,
                                    userId, displayName, "NONE", sheet);
                        }
                    });
        }

        sheet.show();
    }

    private static void fillDynamic(Context context, LinearLayout container, TextView badge,
                                     View dynSep, long userId, String displayName,
                                     String status, BottomSheetDialog sheet) {
        container.post(() -> {
            container.removeAllViews();

            if (STATUS_FRIEND.equals(status)) {
                showBadge(context, badge, "• Bạn bè", COLOR_BADGE_GREEN);

            } else if (STATUS_PENDING_SENT.equals(status)) {
                showBadge(context, badge, "• Đã gửi lời mời kết bạn", COLOR_BADGE_ORG);
                // No action row — no separator needed

            } else if (STATUS_PENDING_RECEIVED.equals(status)) {
                showBadge(context, badge, "• Muốn kết bạn với bạn", COLOR_BADGE_BLUE);
                addRow(context, container, "Chấp nhận lời mời kết bạn", COLOR_TEXT, v -> {
                    sheet.dismiss();
                    RetrofitClient.loadToken(context);
                    RetrofitClient.getClient().create(FriendApiService.class)
                            .acceptFriend(userId).enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call,
                                                       Response<ApiResponse<Void>> response) {
                                    Toast.makeText(context,
                                            "Đã kết bạn với " + displayName,
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    Toast.makeText(context, "Lỗi kết nối",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                });

            } else if ("BLOCKED".equals(status)) {
                showBadge(context, badge, "• Đã chặn", COLOR_DESTRUCTIVE);
                // Nhắn tin vẫn là action riêng bên dưới, helper sẽ hiện trạng thái block.

            } else {
                addRow(context, container, "Kết bạn", COLOR_TEXT, v -> {
                    sheet.dismiss();
                    RetrofitClient.loadToken(context);
                    RetrofitClient.getClient().create(FriendApiService.class)
                            .sendFriendRequest(userId).enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call,
                                                       Response<ApiResponse<Void>> response) {
                                    Toast.makeText(context,
                                            response.isSuccessful()
                                                    ? "Đã gửi lời mời kết bạn đến " + displayName
                                                    : "Không thể gửi lời mời",
                                            Toast.LENGTH_SHORT).show();
                                }
                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    Toast.makeText(context, "Lỗi kết nối",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                });
            }

            dynSep.setVisibility(container.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        });
    }

    private static void showBadge(Context context, TextView badge, String text, int color) {
        badge.post(() -> {
            badge.setVisibility(View.VISIBLE);
            badge.setText(text);
            badge.setTextColor(color);
            badge.setTypeface(null, Typeface.BOLD);
        });
    }

    private static boolean asBoolean(java.util.Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return false;
        Object value = map.get(key);
        return value instanceof Boolean && (Boolean) value;
    }

    // ── Private builders ──

    /** Full-width centered text row with ripple feedback. */
    private static void addRow(Context context, LinearLayout parent, String text,
                                int textColor, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(17);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dpToPx(context, 20), dpToPx(context, 15),
                dpToPx(context, 20), dpToPx(context, 15));
        if (listener != null) {
            tv.setClickable(true);
            tv.setFocusable(true);
            android.util.TypedValue tv2 = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv2, true);
            tv.setBackgroundResource(tv2.resourceId);
            tv.setOnClickListener(listener);
        }
        parent.addView(tv, matchWidth());
    }

    private static LinearLayout roundedGroup(Context context) {
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_SURFACE);
        bg.setCornerRadius(dpToPx(context, 14));
        ll.setBackground(bg);
        // Clip to the rounded shape so row ripples stay inside
        ll.setClipToOutline(true);
        return ll;
    }

    private static View hairline(Context context) {
        View v = new View(context);
        v.setBackgroundColor(COLOR_SEPARATOR);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private static LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    // ── Public helpers used by ConversationActivity chat menu ──

    public static void addActionRow(Context context, LinearLayout parent, String icon,
                                     String label, int textColor,
                                     View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(context, 20), dpToPx(context, 16),
                dpToPx(context, 20), dpToPx(context, 16));

        if (listener != null) {
            row.setClickable(true);
            row.setFocusable(true);
            android.util.TypedValue tv = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            row.setBackgroundResource(tv.resourceId);
            row.setOnClickListener(listener);
        }

        if (icon != null && !icon.isEmpty()) {
            TextView tvIcon = new TextView(context);
            tvIcon.setText(icon);
            tvIcon.setTextSize(18);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            iconLp.setMarginEnd(dpToPx(context, 16));
            tvIcon.setLayoutParams(iconLp);
            row.addView(tvIcon);
        }

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(16);
        tvLabel.setTextColor(textColor);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tvLabel);

        parent.addView(row);
    }

    public static void addSeparator(Context context, LinearLayout parent) {
        View sep = new View(context);
        sep.setBackgroundColor(COLOR_SEPARATOR);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(sep);
    }
}
