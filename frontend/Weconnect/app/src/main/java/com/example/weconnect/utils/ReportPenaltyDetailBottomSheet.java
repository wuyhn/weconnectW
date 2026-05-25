package com.example.weconnect.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.weconnect.api.ReportApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportPenaltyDetailBottomSheet {

    private static final int COLOR_TEXT      = 0xFF2D2D2D;
    private static final int COLOR_MUTED     = 0xFF757575;
    private static final int COLOR_WHITE     = 0xFFFFFFFF;
    private static final int COLOR_SEPARATOR = 0xFFE5E7EB;
    private static final int COLOR_RED       = 0xFFDC2626;
    private static final int COLOR_WARN_BG   = 0xFFFEF3C7;
    private static final int COLOR_WARN_TEXT = 0xFFB45309;
    private static final int COLOR_WARN_SUB  = 0xFF92400E;
    private static final int COLOR_PINK      = 0xFFFF4D6D;

    public static void show(Context context, long reportId) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(0x00000000);
        int side = dp(context, 12);
        scrollView.setPadding(side, 0, side, side);
        scrollView.setClipToPadding(false);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_WHITE);
        float r = dp(context, 24);
        cardBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        scrollView.addView(card, matchW());

        // Drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(COLOR_SEPARATOR);
        handleBg.setCornerRadius(dp(context, 3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp =
                new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
        handleLp.gravity      = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin    = dp(context, 12);
        handleLp.bottomMargin = dp(context, 4);
        card.addView(handle, handleLp);

        // Header
        FrameLayout header = new FrameLayout(context);
        header.setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 14));

        TextView titleTv = new TextView(context);
        titleTv.setText("Chi tiết xử lý báo cáo");
        titleTv.setTextSize(18);
        titleTv.setTextColor(COLOR_TEXT);
        titleTv.setTypeface(null, Typeface.BOLD);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity     = Gravity.CENTER_VERTICAL;
        titleLp.rightMargin = dp(context, 44);
        header.addView(titleTv, titleLp);

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(16);
        closeBtn.setTextColor(0xFF9CA3AF);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        android.util.TypedValue rpl = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rpl, true);
        closeBtn.setBackgroundResource(rpl.resourceId);
        closeBtn.setOnClickListener(v -> sheet.dismiss());
        int btnSz = dp(context, 36);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(btnSz, btnSz);
        closeLp.gravity = Gravity.TOP | Gravity.END;
        header.addView(closeBtn, closeLp);

        card.addView(header, matchW());
        addDivider(context, card);

        // Loading
        ProgressBar progressBar = new ProgressBar(context);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLp.gravity      = Gravity.CENTER_HORIZONTAL;
        pbLp.topMargin    = dp(context, 40);
        pbLp.bottomMargin = dp(context, 40);
        card.addView(progressBar, pbLp);

        // Error state
        LinearLayout layoutError = new LinearLayout(context);
        layoutError.setOrientation(LinearLayout.VERTICAL);
        layoutError.setGravity(Gravity.CENTER);
        layoutError.setVisibility(View.GONE);
        layoutError.setPadding(dp(context, 24), dp(context, 36), dp(context, 24), dp(context, 24));

        TextView tvError = new TextView(context);
        tvError.setText("Không thể tải chi tiết báo cáo. Vui lòng thử lại sau.");
        tvError.setTextSize(14);
        tvError.setTextColor(COLOR_MUTED);
        tvError.setGravity(Gravity.CENTER);
        layoutError.addView(tvError, matchW());
        card.addView(layoutError, matchW());

        // Content (hidden until data loaded)
        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setVisibility(View.GONE);
        contentLayout.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0);
        card.addView(contentLayout, matchW());

        // "Đã hiểu" button
        TextView btnUnderstood = new TextView(context);
        btnUnderstood.setText("Đã hiểu");
        btnUnderstood.setTextSize(16);
        btnUnderstood.setTextColor(COLOR_WHITE);
        btnUnderstood.setTypeface(null, Typeface.BOLD);
        btnUnderstood.setGravity(Gravity.CENTER);
        btnUnderstood.setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
        btnUnderstood.setVisibility(View.GONE);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(COLOR_PINK);
        btnBg.setCornerRadius(dp(context, 26));
        btnUnderstood.setBackground(btnBg);
        btnUnderstood.setClickable(true);
        btnUnderstood.setFocusable(true);
        btnUnderstood.setOnClickListener(v -> sheet.dismiss());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 52));
        btnLp.setMargins(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 24));
        card.addView(btnUnderstood, btnLp);

        sheet.setContentView(scrollView);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.show();

        if (reportId <= 0) {
            progressBar.setVisibility(View.GONE);
            layoutError.setVisibility(View.VISIBLE);
            btnUnderstood.setVisibility(View.VISIBLE);
            return;
        }

        RetrofitClient.loadToken(context);
        ReportApiService api = RetrofitClient.getClient().create(ReportApiService.class);
        api.getMyReportDetail(reportId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    bindData(context, contentLayout, response.body().getResult());
                    contentLayout.setVisibility(View.VISIBLE);
                } else {
                    layoutError.setVisibility(View.VISIBLE);
                }
                btnUnderstood.setVisibility(View.VISIBLE);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                layoutError.setVisibility(View.VISIBLE);
                btnUnderstood.setVisibility(View.VISIBLE);
            }
        });
    }

    private static void bindData(Context context, LinearLayout contentLayout,
                                  Map<String, Object> data) {
        // Warning banner
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(COLOR_WARN_BG);
        bannerBg.setCornerRadius(dp(context, 10));
        banner.setBackground(bannerBg);
        banner.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
        banner.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerLp.bottomMargin = dp(context, 14);

        TextView emojiTv = new TextView(context);
        emojiTv.setText("⚠️");
        emojiTv.setTextSize(20);
        LinearLayout.LayoutParams emojiLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        emojiLp.setMarginEnd(dp(context, 10));
        banner.addView(emojiTv, emojiLp);

        LinearLayout bannerText = new LinearLayout(context);
        bannerText.setOrientation(LinearLayout.VERTICAL);

        TextView bannerTitle = new TextView(context);
        bannerTitle.setText("Báo cáo đã được xác nhận");
        bannerTitle.setTextSize(15);
        bannerTitle.setTextColor(COLOR_WARN_TEXT);
        bannerTitle.setTypeface(null, Typeface.BOLD);
        bannerText.addView(bannerTitle, matchW());

        TextView bannerSub = new TextView(context);
        bannerSub.setText("Hoạt động của bạn đã bị xác nhận là vi phạm quy định cộng đồng.");
        bannerSub.setTextSize(13);
        bannerSub.setTextColor(COLOR_WARN_SUB);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(context, 2);
        bannerText.addView(bannerSub, subLp);

        banner.addView(bannerText,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        contentLayout.addView(banner, bannerLp);

        // Info card
        LinearLayout infoCard = new LinearLayout(context);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg2 = new GradientDrawable();
        cardBg2.setColor(COLOR_WHITE);
        cardBg2.setCornerRadius(dp(context, 12));
        cardBg2.setStroke(1, COLOR_SEPARATOR);
        infoCard.setBackground(cardBg2);
        infoCard.setClipToOutline(true);
        infoCard.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 4));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(context, 4);

        // Trạng thái xử lý
        String status = data.get("status") != null ? data.get("status").toString() : "";
        String statusDisplay;
        int statusColor;
        if ("VALID".equals(status)) {
            statusDisplay = "Đã xác nhận vi phạm";
            statusColor   = COLOR_RED;
        } else if ("REJECTED".equals(status)) {
            statusDisplay = "Không vi phạm";
            statusColor   = 0xFF16A34A;
        } else {
            statusDisplay = "Đang xử lý";
            statusColor   = 0xFFF59E0B;
        }
        addRow(context, infoCard, "TRẠNG THÁI XỬ LÝ", statusDisplay, statusColor, false);

        // Loại báo cáo
        String targetType  = data.get("targetType") != null ? data.get("targetType").toString() : "";
        String typeDisplay = "USER".equals(targetType) ? "Báo cáo người dùng" : "Báo cáo bài viết";
        addRow(context, infoCard, "LOẠI BÁO CÁO", typeDisplay, COLOR_TEXT, true);

        // Lý do vi phạm
        String reason = data.get("reason") != null ? data.get("reason").toString() : "—";
        addRow(context, infoCard, "LÝ DO VI PHẠM", reason, COLOR_TEXT, true);

        // Điểm uy tín bị trừ
        int penalty = data.get("penaltyPoint") != null
                ? ((Number) data.get("penaltyPoint")).intValue() : 0;
        addRow(context, infoCard, "ĐIỂM UY TÍN BỊ TRỪ", "−" + penalty + " điểm",
                COLOR_RED, true);

        // Điểm uy tín hiện tại
        String scoreText;
        if (data.get("currentReputationScore") != null) {
            int score = (int) Math.round(
                    ((Number) data.get("currentReputationScore")).doubleValue());
            scoreText = score + " / 100";
        } else {
            scoreText = "—";
        }
        addRow(context, infoCard, "ĐIỂM UY TÍN HIỆN TẠI", scoreText, COLOR_TEXT, true);

        // Ghi chú từ admin (ẩn nếu không có)
        String adminNote = data.get("adminAction") != null
                ? data.get("adminAction").toString().trim() : "";
        if (!adminNote.isEmpty()) {
            addRow(context, infoCard, "GHI CHÚ TỪ ADMIN", adminNote, COLOR_TEXT, true);
        }

        // Thời gian xử lý
        String reviewedAt = data.get("reviewedAt") != null
                ? data.get("reviewedAt").toString() : null;
        addRow(context, infoCard, "THỜI GIAN XỬ LÝ", formatDateTime(reviewedAt), COLOR_TEXT, true);

        contentLayout.addView(infoCard, cardLp);
    }

    private static void addRow(Context context, LinearLayout parent,
                                String label, String value, int valueColor,
                                boolean withTopDivider) {
        if (withTopDivider) {
            addDivider(context, parent);
            View spacer = new View(context);
            spacer.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 12)));
            parent.addView(spacer);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(context, 12);

        TextView labelTv = new TextView(context);
        labelTv.setText(label);
        labelTv.setTextSize(11);
        labelTv.setTextColor(COLOR_MUTED);
        labelTv.setLetterSpacing(0.08f);
        row.addView(labelTv, matchW());

        TextView valueTv = new TextView(context);
        valueTv.setText(value);
        valueTv.setTextSize(15);
        valueTv.setTextColor(valueColor);
        LinearLayout.LayoutParams valLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valLp.topMargin = dp(context, 3);
        row.addView(valueTv, valLp);

        parent.addView(row, rowLp);
    }

    private static void addDivider(Context context, LinearLayout parent) {
        View div = new View(context);
        div.setBackgroundColor(COLOR_SEPARATOR);
        parent.addView(div, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private static String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = in.parse(raw);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault());
            return out.format(date);
        } catch (ParseException e) {
            return raw;
        }
    }

    private static LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
