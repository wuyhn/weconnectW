package com.example.weconnect.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.bumptech.glide.Glide;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.ReportApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.UserReview;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public final class ReviewReportBottomSheet {

    // ── Colour palette (matches UserReportBottomSheet) ──
    private static final int COLOR_TEXT      = 0xFF1C1C1E;
    private static final int COLOR_MUTED     = 0xFF6B7280;
    private static final int COLOR_PINK      = 0xFFE91E8C;
    private static final int COLOR_PINK_BG   = 0xFFFCE4EC;
    private static final int COLOR_SEPARATOR = 0xFFE5E7EB;
    private static final int COLOR_SURFACE   = 0xFFF9FAFB;
    private static final int COLOR_WHITE     = 0xFFFFFFFF;

    private static final String[] REASONS = {
            "Spam",
            "Nội dung xúc phạm hoặc không phù hợp",
            "Thông tin sai lệch",
            "Lý do khác"
    };
    private static final int IDX_OTHER = 3;

    // ── Headless Fragment to hold the ActivityResultLauncher (single image) ──
    public static class PickerFragment extends Fragment {
        static final String TAG = "review_report_img_picker";
        private ActivityResultLauncher<String> launcher;
        private Consumer<Uri> pendingCallback;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            launcher = registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (pendingCallback != null && uri != null) {
                            pendingCallback.accept(uri);
                        }
                        pendingCallback = null;
                    });
        }

        void launch(Consumer<Uri> callback) {
            pendingCallback = callback;
            launcher.launch("image/*");
        }
    }

    // ── Entry point ──

    public static void show(Context context, FragmentManager fm, UserReview review) {
        // Ensure PickerFragment is present in this FragmentManager
        PickerFragment picker = (PickerFragment) fm.findFragmentByTag(PickerFragment.TAG);
        if (picker == null) {
            picker = new PickerFragment();
            fm.beginTransaction()
                    .add(picker, PickerFragment.TAG)
                    .commitNowAllowingStateLoss();
        }
        final PickerFragment finalPicker = picker;

        // Shared mutable state
        final Uri[]      selectedUri  = {null};
        int[]            selectedIdx  = {-1};
        LinearLayout[]   tileRefs     = new LinearLayout[REASONS.length];
        View[]           radioRefs    = new View[REASONS.length];
        TextView[]       textRefs     = new TextView[REASONS.length];
        EditText[]       descRef      = new EditText[1];
        TextView[]       submitRef    = new TextView[1];
        LinearLayout[]   otherSection = new LinearLayout[1];
        ImageView[]      ivPreviewRef = new ImageView[1];
        TextView[]       tvClearRef   = new TextView[1];

        BottomSheetDialog sheet = new BottomSheetDialog(context);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }

        // ── Root scroll ──
        NestedScrollView scroll = new NestedScrollView(context);
        scroll.setBackgroundColor(0x00000000);
        int side = dp(context, 12);
        scroll.setPadding(side, 0, side, side);
        scroll.setClipToPadding(false);

        // ── Unified card ──
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_WHITE);
        float r = dp(context, 24);
        cardBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        scroll.addView(card, matchW());

        // ── Drag handle ──
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(COLOR_SEPARATOR);
        handleBg.setCornerRadius(dp(context, 3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp =
                new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(context, 12);
        handleLp.bottomMargin = dp(context, 4);
        card.addView(handle, handleLp);

        // ── Header ──
        FrameLayout header = new FrameLayout(context);
        header.setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 14));

        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText("Báo cáo đánh giá");
        titleTv.setTextSize(18);
        titleTv.setTextColor(COLOR_TEXT);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleBlock.addView(titleTv, matchW());

        TextView subtitleTv = new TextView(context);
        subtitleTv.setText("Hãy cho chúng tôi biết vấn đề bạn gặp phải với đánh giá này.");
        subtitleTv.setTextSize(13);
        subtitleTv.setTextColor(COLOR_MUTED);
        subtitleTv.setPadding(0, dp(context, 5), dp(context, 40), 0);
        titleBlock.addView(subtitleTv, matchW());
        header.addView(titleBlock, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        TextView closeBtn = new TextView(context);
        closeBtn.setText("✕");
        closeBtn.setTextSize(16);
        closeBtn.setTextColor(0xFF9CA3AF);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        android.util.TypedValue rplClose = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rplClose, true);
        closeBtn.setBackgroundResource(rplClose.resourceId);
        closeBtn.setOnClickListener(v -> sheet.dismiss());
        int btnSz = dp(context, 36);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(btnSz, btnSz);
        closeLp.gravity = Gravity.TOP | Gravity.END;
        header.addView(closeBtn, closeLp);
        card.addView(header, matchW());

        addDivider(context, card, 0);

        // ── Reasons label ──
        TextView reasonsLabel = makeLabel(context, "Lý do báo cáo");
        reasonsLabel.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 10));
        card.addView(reasonsLabel, matchW());

        // ── Reason tiles ──
        LinearLayout tilesWrap = new LinearLayout(context);
        tilesWrap.setOrientation(LinearLayout.VERTICAL);
        tilesWrap.setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 16));

        for (int i = 0; i < REASONS.length; i++) {
            final int idx = i;
            if (i > 0) addSpacer(context, tilesWrap, 8);

            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setBackground(makeTileBg(false, context));
            tile.setClipToOutline(true);

            View radio = new View(context);
            radio.setBackground(makeRadioBg(false, context));
            int radioSz = dp(context, 20);
            tile.addView(radio, new LinearLayout.LayoutParams(radioSz, radioSz));

            TextView reasonTv = new TextView(context);
            reasonTv.setText(REASONS[i]);
            reasonTv.setTextSize(15);
            reasonTv.setTextColor(COLOR_TEXT);
            LinearLayout.LayoutParams textLp =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textLp.setMarginStart(dp(context, 12));
            tile.addView(reasonTv, textLp);

            tileRefs[i]  = tile;
            radioRefs[i] = radio;
            textRefs[i]  = reasonTv;

            tile.setOnClickListener(v -> {
                int prev = selectedIdx[0];
                if (prev >= 0 && prev != idx) {
                    tileRefs[prev].setBackground(makeTileBg(false, context));
                    radioRefs[prev].setBackground(makeRadioBg(false, context));
                    textRefs[prev].setTextColor(COLOR_TEXT);
                    textRefs[prev].setTypeface(null, Typeface.NORMAL);
                }
                selectedIdx[0] = idx;
                tile.setBackground(makeTileBg(true, context));
                radio.setBackground(makeRadioBg(true, context));
                reasonTv.setTextColor(COLOR_PINK);
                reasonTv.setTypeface(null, Typeface.BOLD);

                boolean isOther = (idx == IDX_OTHER);
                if (otherSection[0] != null) {
                    otherSection[0].setVisibility(isOther ? View.VISIBLE : View.GONE);
                }
                if (!isOther && descRef[0] != null) {
                    descRef[0].setText("");
                    descRef[0].setError(null);
                }
                updateSubmitState(submitRef[0], selectedIdx[0], descRef[0]);
            });

            tilesWrap.addView(tile, matchW());
        }
        card.addView(tilesWrap, matchW());

        addDivider(context, card, 0);

        // ── "Lý do khác" expanded section (GONE by default) ──
        LinearLayout otherReasonSection = new LinearLayout(context);
        otherReasonSection.setOrientation(LinearLayout.VERTICAL);
        otherReasonSection.setVisibility(View.GONE);
        otherSection[0] = otherReasonSection;

        // Detail text input
        TextView descLabel = makeLabel(context, "Mô tả chi tiết");
        descLabel.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 8));
        otherReasonSection.addView(descLabel, matchW());

        LinearLayout descWrap = new LinearLayout(context);
        descWrap.setOrientation(LinearLayout.VERTICAL);
        descWrap.setPadding(dp(context, 16), 0, dp(context, 16), 0);

        EditText descInput = new EditText(context);
        descRef[0] = descInput;
        descInput.setHint("Nhập mô tả chi tiết...");
        descInput.setTextSize(15);
        descInput.setTextColor(COLOR_TEXT);
        descInput.setMinLines(3);
        descInput.setMaxLines(6);
        descInput.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        GradientDrawable descBg = new GradientDrawable();
        descBg.setColor(COLOR_SURFACE);
        descBg.setCornerRadius(dp(context, 10));
        descBg.setStroke(1, COLOR_SEPARATOR);
        descInput.setBackground(descBg);
        descInput.setGravity(Gravity.TOP | Gravity.START);
        descInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (descInput.getText() != null && !descInput.getText().toString().trim().isEmpty()) {
                    descInput.setError(null);
                }
                updateSubmitState(submitRef[0], selectedIdx[0], descInput);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        descWrap.addView(descInput, matchW());

        TextView descHint = new TextView(context);
        descHint.setText("Bắt buộc khi chọn 'Lý do khác'");
        descHint.setTextSize(12);
        descHint.setTextColor(COLOR_MUTED);
        descHint.setPadding(dp(context, 4), dp(context, 6), 0, dp(context, 12));
        descWrap.addView(descHint, matchW());
        otherReasonSection.addView(descWrap, matchW());

        // ── Image evidence section (inside "Lý do khác") ──
        TextView imgLabel = makeLabel(context, "Ảnh minh chứng (tuỳ chọn)");
        imgLabel.setPadding(dp(context, 20), dp(context, 4), dp(context, 20), dp(context, 10));
        otherReasonSection.addView(imgLabel, matchW());

        LinearLayout imgRow = new LinearLayout(context);
        imgRow.setOrientation(LinearLayout.HORIZONTAL);
        imgRow.setGravity(Gravity.CENTER_VERTICAL);
        imgRow.setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 16));

        // Image preview box (tap to pick)
        FrameLayout ivFrame = new FrameLayout(context);
        int ivSize = dp(context, 84);
        LinearLayout.LayoutParams ivFrameLp = new LinearLayout.LayoutParams(ivSize, ivSize);
        ivFrameLp.setMarginEnd(dp(context, 14));
        ivFrame.setLayoutParams(ivFrameLp);

        ImageView ivPreview = new ImageView(context);
        ivPreviewRef[0] = ivPreview;
        ivPreview.setLayoutParams(new FrameLayout.LayoutParams(ivSize, ivSize));
        ivPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivPreview.setClipToOutline(true);
        GradientDrawable ivBg = new GradientDrawable();
        ivBg.setColor(COLOR_SURFACE);
        ivBg.setCornerRadius(dp(context, 10));
        ivBg.setStroke(1, COLOR_SEPARATOR);
        ivPreview.setBackground(ivBg);
        ivFrame.addView(ivPreview);

        // Camera icon overlay (visible when no image selected)
        TextView tvCameraHint = new TextView(context);
        tvCameraHint.setText("📷");
        tvCameraHint.setTextSize(22);
        tvCameraHint.setGravity(Gravity.CENTER);
        tvCameraHint.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ivFrame.addView(tvCameraHint);
        ivFrame.setClickable(true);
        ivFrame.setFocusable(true);
        android.util.TypedValue rplImg = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rplImg, true);
        ivFrame.setBackgroundResource(rplImg.resourceId);
        imgRow.addView(ivFrame);

        // Right side: instruction + clear button
        LinearLayout imgTextCol = new LinearLayout(context);
        imgTextCol.setOrientation(LinearLayout.VERTICAL);
        imgTextCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvAddImg = new TextView(context);
        tvAddImg.setText("Thêm ảnh bằng chứng");
        tvAddImg.setTextSize(14);
        tvAddImg.setTextColor(COLOR_PINK);
        tvAddImg.setTypeface(null, Typeface.BOLD);
        imgTextCol.addView(tvAddImg, matchW());

        TextView tvImgSub = new TextView(context);
        tvImgSub.setText("jpg, png · tối đa 5MB");
        tvImgSub.setTextSize(12);
        tvImgSub.setTextColor(COLOR_MUTED);
        tvImgSub.setPadding(0, dp(context, 3), 0, 0);
        imgTextCol.addView(tvImgSub, matchW());

        TextView tvClear = new TextView(context);
        tvClearRef[0] = tvClear;
        tvClear.setText("✕  Xóa ảnh");
        tvClear.setTextSize(12);
        tvClear.setTextColor(0xFFEF4444);
        tvClear.setVisibility(View.GONE);
        tvClear.setPadding(0, dp(context, 6), 0, 0);
        tvClear.setClickable(true);
        tvClear.setFocusable(true);
        tvClear.setOnClickListener(v -> {
            selectedUri[0] = null;
            ivPreview.setImageDrawable(null);
            ivPreview.setBackground(ivBg);
            tvCameraHint.setVisibility(View.VISIBLE);
            tvClear.setVisibility(View.GONE);
        });
        imgTextCol.addView(tvClear, matchW());
        imgRow.addView(imgTextCol);
        otherReasonSection.addView(imgRow, matchW());

        card.addView(otherReasonSection, matchW());

        // Image picker click
        ivFrame.setOnClickListener(v -> {
            try {
                finalPicker.launch(uri -> {
                    selectedUri[0] = uri;
                    Glide.with(context).load(uri).centerCrop().into(ivPreview);
                    tvCameraHint.setVisibility(View.GONE);
                    tvClear.setVisibility(View.VISIBLE);
                });
            } catch (Exception e) {
                Toast.makeText(context, "Không thể mở thư viện ảnh", Toast.LENGTH_SHORT).show();
            }
        });

        addDivider(context, card, 0);

        // ── Footer ──
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 24));

        TextView submit = new TextView(context);
        submitRef[0] = submit;
        submit.setText("Gửi báo cáo");
        submit.setTextSize(16);
        submit.setTextColor(COLOR_WHITE);
        submit.setTypeface(null, Typeface.BOLD);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
        submit.setEnabled(false);
        submit.setAlpha(0.45f);
        GradientDrawable submitBg = new GradientDrawable();
        submitBg.setColor(COLOR_PINK);
        submitBg.setCornerRadius(dp(context, 12));
        submit.setBackground(submitBg);
        submit.setClickable(true);
        submit.setFocusable(true);
        submit.setOnClickListener(v -> {
            if (!submit.isEnabled()) return;
            String reason = REASONS[selectedIdx[0]];
            String desc = descInput.getText() != null
                    ? descInput.getText().toString().trim() : "";
            if (selectedIdx[0] == IDX_OTHER && desc.isEmpty()) {
                descInput.setError("Vui lòng nhập mô tả chi tiết.");
                descInput.requestFocus();
                return;
            }
            submit.setEnabled(false);
            submit.setAlpha(0.45f);
            submit.setText("Đang gửi...");
            doSubmit(context, sheet, review, reason, desc, selectedUri[0], submit);
        });
        footer.addView(submit, matchW());

        TextView cancel = new TextView(context);
        cancel.setText("Hủy");
        cancel.setTextSize(15);
        cancel.setTextColor(COLOR_MUTED);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), 0);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        android.util.TypedValue rplCancel = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rplCancel, true);
        cancel.setBackgroundResource(rplCancel.resourceId);
        cancel.setOnClickListener(v -> sheet.dismiss());
        footer.addView(cancel, matchW());

        card.addView(footer, matchW());

        sheet.setContentView(scroll);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        sheet.show();
    }

    // ── Submit flow: tái sử dụng pipeline report đã có ──
    //
    // Luồng:
    //   1. Nếu có ảnh → compress → upload lên /api/upload/image → nhận URL
    //   2. Gọi /api/reports với body JSON {targetType, targetId, reason, description, evidenceImages}
    //      (cùng endpoint với report user/post — không cần endpoint multipart riêng)
    //

    private static void doSubmit(Context context, BottomSheetDialog sheet,
                                  UserReview review, String reason, String desc,
                                  Uri imageUri, TextView submit) {
        RetrofitClient.loadToken(context);

        if (imageUri != null) {
            // Bước 1: compress ảnh trên background thread
            new Thread(() -> {
                byte[] bytes = compressImageUri(context, imageUri);
                runOnMain(() -> {
                    if (bytes == null) {
                        // Compress thất bại → gửi báo cáo không kèm ảnh
                        sendReport(context, sheet, review, reason, desc, null, submit);
                        return;
                    }
                    // Bước 2: upload ảnh lên server → nhận URL
                    RequestBody imageBody = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
                    MultipartBody.Part part = MultipartBody.Part.createFormData(
                            "file", "review_evidence.jpg", imageBody);
                    RetrofitClient.getClient().create(PostApiService.class)
                            .uploadImage(part)
                            .enqueue(new Callback<ApiResponse<String>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<String>> c,
                                                       Response<ApiResponse<String>> resp) {
                                    String url = (resp.isSuccessful() && resp.body() != null)
                                            ? resp.body().getResult() : null;
                                    // Bước 3: gửi báo cáo JSON với URL ảnh (hoặc null nếu upload lỗi)
                                    sendReport(context, sheet, review, reason, desc, url, submit);
                                }

                                @Override
                                public void onFailure(Call<ApiResponse<String>> c, Throwable t) {
                                    // Upload ảnh thất bại → vẫn gửi báo cáo, chỉ không kèm ảnh
                                    sendReport(context, sheet, review, reason, desc, null, submit);
                                }
                            });
                });
            }).start();
        } else {
            sendReport(context, sheet, review, reason, desc, null, submit);
        }
    }

    // Gửi báo cáo JSON qua /api/reports — cùng pipeline với report user/post
    private static void sendReport(Context context, BottomSheetDialog sheet,
                                    UserReview review,
                                    String reason,    // category text: "Spam", "Lý do khác", ...
                                    String desc,      // mô tả chi tiết (chỉ có cho "Lý do khác")
                                    String imageUrl,  // URL ảnh bằng chứng hoặc null
                                    TextView submit) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetType", "REVIEW");
        body.put("targetId", review.getId());
        body.put("reason", reason);
        if (desc != null && !desc.isEmpty()) {
            body.put("description", desc);
        }
        if (imageUrl != null && !imageUrl.isEmpty()) {
            body.put("evidenceImages", Collections.singletonList(imageUrl));
        }

        RetrofitClient.getClient().create(ReportApiService.class)
                .createReport(body)
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Void>> c,
                                           Response<ApiResponse<Void>> response) {
                        if (response.isSuccessful()) {
                            sheet.dismiss();
                            Toast.makeText(context,
                                    "Đã gửi báo cáo. Chúng tôi sẽ xem xét trong thời gian sớm nhất.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            resetSubmit(submit);
                            String errMsg = "Không thể gửi báo cáo. Vui lòng thử lại. (" + response.code() + ")";
                            try {
                                if (response.errorBody() != null) {
                                    android.util.Log.e("ReviewReport",
                                            "HTTP " + response.code() + ": " + response.errorBody().string());
                                }
                            } catch (Exception ignored) {}
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Void>> c, Throwable t) {
                        resetSubmit(submit);
                        Toast.makeText(context,
                                "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        android.util.Log.e("ReviewReport", "sendReport onFailure", t);
                    }
                });
    }

    // ── Image compression (matches UserReportBottomSheet pattern) ──

    private static byte[] compressImageUri(Context context, Uri uri) {
        try {
            BitmapFactory.Options sizeOpts = new BitmapFactory.Options();
            sizeOpts.inJustDecodeBounds = true;
            try (InputStream probe = context.getContentResolver().openInputStream(uri)) {
                if (probe == null) return null;
                BitmapFactory.decodeStream(probe, null, sizeOpts);
            }
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = calculateInSampleSize(sizeOpts, 1280, 1280);
            decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap;
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                bitmap = BitmapFactory.decodeStream(is, null, decodeOpts);
            }
            if (bitmap == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            bitmap.recycle();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options opts, int maxW, int maxH) {
        int s = 1;
        if (opts.outHeight > maxH || opts.outWidth > maxW) {
            int hh = opts.outHeight / 2, hw = opts.outWidth / 2;
            while ((hh / s) >= maxH && (hw / s) >= maxW) s *= 2;
        }
        return s;
    }

    // ── State helpers ──

    private static void updateSubmitState(TextView submit, int idx, EditText desc) {
        if (submit == null) return;
        boolean ok = idx >= 0;
        if (ok && idx == IDX_OTHER) {
            String t = desc != null && desc.getText() != null
                    ? desc.getText().toString().trim() : "";
            ok = !t.isEmpty();
        }
        submit.setEnabled(ok);
        submit.setAlpha(ok ? 1.0f : 0.45f);
    }

    private static void resetSubmit(TextView submit) {
        if (submit == null) return;
        submit.setEnabled(true);
        submit.setAlpha(1.0f);
        submit.setText("Gửi báo cáo");
    }

    private static void runOnMain(Runnable r) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(r);
    }

    // ── Drawable builders (consistent with UserReportBottomSheet) ──

    private static GradientDrawable makeTileBg(boolean selected, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(ctx, 10));
        if (selected) {
            gd.setColor(COLOR_PINK_BG);
            gd.setStroke(dp(ctx, 2), COLOR_PINK);
        } else {
            gd.setColor(COLOR_WHITE);
            gd.setStroke(1, COLOR_SEPARATOR);
        }
        return gd;
    }

    private static GradientDrawable makeRadioBg(boolean selected, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (selected) {
            gd.setColor(COLOR_PINK);
        } else {
            gd.setColor(COLOR_WHITE);
            gd.setStroke(dp(ctx, 2), COLOR_SEPARATOR);
        }
        return gd;
    }

    // ── Layout helpers ──

    private static TextView makeLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(COLOR_MUTED);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.04f);
        return tv;
    }

    private static void addDivider(Context ctx, LinearLayout parent, int topDp) {
        View div = new View(ctx);
        div.setBackgroundColor(COLOR_SEPARATOR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        if (topDp > 0) lp.topMargin = dp(ctx, topDp);
        parent.addView(div, lp);
    }

    private static void addSpacer(Context ctx, LinearLayout parent, int heightDp) {
        View s = new View(ctx);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, heightDp)));
        parent.addView(s);
    }

    private static LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
