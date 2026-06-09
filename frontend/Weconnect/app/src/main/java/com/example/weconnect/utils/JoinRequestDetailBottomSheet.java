package com.example.weconnect.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import java.util.Map;

public final class JoinRequestDetailBottomSheet {

    public interface OnActionListener {
        void onApprove();
        void onReject();
    }

    private JoinRequestDetailBottomSheet() {}

    public static void show(Context context, Map<String, Object> member,
                            long postId, int memberCount, int maxMembers,
                            OnActionListener listener) {
        if (context == null || member == null) return;

        BottomSheetDialog sheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context)
                .inflate(R.layout.bottom_sheet_join_request_detail, null);
        sheet.setContentView(view);

        bindData(view, member, memberCount, maxMembers, sheet, listener);
        sheet.show();
    }

    private static void bindData(View view, Map<String, Object> member,
                                  int memberCount, int maxMembers,
                                  BottomSheetDialog sheet, OnActionListener listener) {
        ImageView ivAvatar = view.findViewById(R.id.ivDetailAvatar);
        TextView tvName = view.findViewById(R.id.tvDetailName);
        TextView tvReputation = view.findViewById(R.id.tvDetailReputation);
        TextView tvRequesterProvince = view.findViewById(R.id.tvDetailRequesterProvince);
        TextView tvActivityProvince = view.findViewById(R.id.tvDetailActivityProvince);
        LinearLayout layoutWarning = view.findViewById(R.id.layoutDetailWarning);
        TextView tvJoinReason = view.findViewById(R.id.tvDetailJoinReason);
        MaterialButton btnAccept = view.findViewById(R.id.btnDetailAccept);
        MaterialButton btnReject = view.findViewById(R.id.btnDetailReject);

        // Avatar
        String avatarUrl = member.get("avatarUrl") != null
                ? member.get("avatarUrl").toString() : null;
        if (hasText(avatarUrl)) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(view.getContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        // Tên
        long userId = member.get("userId") != null
                ? ((Number) member.get("userId")).longValue() : 0;
        String userName = member.get("userName") != null
                ? member.get("userName").toString() : "Người dùng #" + userId;
        tvName.setText(userName);

        // Điểm uy tín
        int totalReviewCount = member.get("totalReviewCount") != null
                ? ((Number) member.get("totalReviewCount")).intValue() : 0;
        double reputationScore = member.get("reputationScore") != null
                ? ((Number) member.get("reputationScore")).doubleValue() : 100.0;
        float averageRating = member.get("averageRating") != null
                ? ((Number) member.get("averageRating")).floatValue() : 0f;

        if (totalReviewCount == 0) {
            tvReputation.setText("— / Chưa có đánh giá");
        } else {
            String label = reputationLabel(reputationScore);
            tvReputation.setText(String.format("Uy tín: %.0f/100  ·  ⭐ %.1f  ·  %s",
                    reputationScore, averageRating, label));
        }

        // Vị trí
        String requesterProvince = member.get("requesterProvince") != null
                ? member.get("requesterProvince").toString() : null;
        String activityProvince = member.get("activityProvince") != null
                ? member.get("activityProvince").toString() : null;

        tvRequesterProvince.setText("📍 Người dùng ở: "
                + (hasText(requesterProvince) ? requesterProvince : "chưa xác định"));
        tvActivityProvince.setText("📌 Hoạt động tại: "
                + (hasText(activityProvince) ? activityProvince : "chưa xác định"));

        // Warning box
        boolean isFarLocation = Boolean.TRUE.equals(member.get("isFarLocation"));
        layoutWarning.setVisibility(isFarLocation ? View.VISIBLE : View.GONE);

        // Lý do tham gia
        String joinReason = member.get("joinReason") != null
                ? member.get("joinReason").toString() : null;
        if (hasText(joinReason) && !"Cùng địa phương".equalsIgnoreCase(joinReason)) {
            tvJoinReason.setText(joinReason);
        } else {
            tvJoinReason.setText("Không có lý do tham gia.");
            tvJoinReason.setTextColor(0xFF999999);
        }

        // Buttons
        boolean isFull = maxMembers > 0 && memberCount >= maxMembers;
        btnAccept.setEnabled(!isFull);
        btnAccept.setAlpha(isFull ? 0.4f : 1.0f);

        btnAccept.setOnClickListener(v -> {
            sheet.dismiss();
            if (listener != null) listener.onApprove();
        });

        btnReject.setOnClickListener(v -> {
            sheet.dismiss();
            if (listener != null) listener.onReject();
        });
    }

    private static String reputationLabel(double score) {
        if (score >= 80) return "Đáng tin cậy";
        if (score >= 60) return "Bình thường";
        if (score >= 40) return "Cần chú ý";
        return "Thấp";
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
