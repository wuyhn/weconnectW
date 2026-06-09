package com.example.weconnect.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.widget.NestedScrollView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.ReportApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public class UserReportBottomSheet {

    // ── Colour palette ──
    private static final int COLOR_TEXT         = 0xFF1C1C1E;
    private static final int COLOR_MUTED        = 0xFF6B7280;
    private static final int COLOR_BLUE         = 0xFF2563EB;
    private static final int COLOR_BLUE_BG      = 0xFFEEF2FF;
    private static final int COLOR_SEPARATOR    = 0xFFE5E7EB;
    private static final int COLOR_SURFACE      = 0xFFF9FAFB;
    private static final int COLOR_WHITE        = 0xFFFFFFFF;
    private static final int MAX_IMAGES         = 5;

    private static final String[] REASONS = {
            "Tin nhắn làm phiền / spam",
            "Nội dung không phù hợp",
            "Lừa đảo hoặc giả mạo",
            "Quấy rối / xúc phạm",
            "Lý do khác"
    };

    private interface FileUploadApi {
        @Multipart
        @POST("api/upload/image")
        Call<ApiResponse<String>> uploadImage(@Part MultipartBody.Part file);
    }

    // ── Headless Fragment for image picking ──

    public static class PickerFragment extends Fragment {
        private static final String TAG = "report_img_picker";
        private ActivityResultLauncher<String[]> launcher;
        private Consumer<List<Uri>> pendingCallback;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            launcher = registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    uris -> {
                        if (pendingCallback != null && uris != null && !uris.isEmpty()) {
                            // Bug #3 fix: "claim" quyền đọc bền vững cho từng URI trước khi dùng.
                            // Nếu không gọi, URI có thể bị thu hồi sau khi Fragment bị remove
                            // → SecurityException khi doSubmit() mở lại URI.
                            for (Uri u : uris) {
                                try {
                                    requireActivity().getContentResolver()
                                            .takePersistableUriPermission(
                                                    u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                } catch (Exception ignored) {}
                            }
                            pendingCallback.accept(new ArrayList<>(uris));
                        }
                        pendingCallback = null;
                        try {
                            requireActivity().getSupportFragmentManager()
                                    .beginTransaction()
                                    .remove(this)
                                    .commitAllowingStateLoss();
                        } catch (Exception ignored) {}
                    });
        }

        void launch(Consumer<List<Uri>> callback) {
            pendingCallback = callback;
            launcher.launch(new String[]{"image/jpeg", "image/png", "image/webp"});
        }

        static void pick(Context context, Consumer<List<Uri>> callback) {
            AppCompatActivity activity = (AppCompatActivity) context;
            PickerFragment f = (PickerFragment) activity.getSupportFragmentManager()
                    .findFragmentByTag(TAG);
            if (f == null) {
                f = new PickerFragment();
                // Bug #1 fix: commitNow() ném IllegalStateException nếu Activity đã qua
                // onSaveInstanceState (màn hình tắt, cuộc gọi, v.v.) → app crash.
                // commitNowAllowingStateLoss() cho phép commit trong mọi trạng thái lifecycle.
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(f, TAG)
                        .commitNowAllowingStateLoss();
            }
            f.launch(callback);
        }
    }

    // ── Main show() ──

    public static void show(Context context, long userId, String displayName) {
        if (userId <= 0) {
            Toast.makeText(context, "Không thể báo cáo người dùng này", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog sheet = new BottomSheetDialog(context);
        if (sheet.getWindow() != null) {
            sheet.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
        }

        // Shared state
        int[] selectedIndex       = {-1};
        List<Uri> selectedImages  = new ArrayList<>();
        LinearLayout[] tileRefs   = new LinearLayout[REASONS.length];
        View[]         radioRefs  = new View[REASONS.length];
        TextView[]     textRefs   = new TextView[REASONS.length];
        EditText[]     descRef    = new EditText[1];
        TextView[]     submitRef  = new TextView[1];
        LinearLayout[] thumbRef   = new LinearLayout[1];
        LinearLayout[] otherReasonSectionRef = new LinearLayout[1];

        // ── NestedScrollView (root, transparent) ──
        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setBackgroundColor(0x00000000);
        int side = dp(context, 12);
        scrollView.setPadding(side, 0, side, side);
        scrollView.setClipToPadding(false);

        // ── Single unified card (white, rounded top corners) ──
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(COLOR_WHITE);
        float r = dp(context, 24);
        cardBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(cardBg);
        card.setClipToOutline(true);
        scrollView.addView(card, matchW());

        // ── Drag handle ──
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(COLOR_SEPARATOR);
        handleBg.setCornerRadius(dp(context, 3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleLp =
                new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4));
        handleLp.gravity     = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin   = dp(context, 12);
        handleLp.bottomMargin = dp(context, 4);
        card.addView(handle, handleLp);

        // ── Header: title + subtitle + close button ──
        FrameLayout header = new FrameLayout(context);
        header.setPadding(dp(context, 20), dp(context, 10), dp(context, 16), dp(context, 14));

        LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(context);
        titleTv.setText("Báo cáo người dùng");
        titleTv.setTextSize(18);
        titleTv.setTextColor(COLOR_TEXT);
        titleTv.setTypeface(null, Typeface.BOLD);
        titleBlock.addView(titleTv, matchW());

        TextView subtitleTv = new TextView(context);
        subtitleTv.setText("Hãy cho chúng tôi biết vấn đề bạn gặp phải với người dùng này.");
        subtitleTv.setTextSize(13);
        subtitleTv.setTextColor(COLOR_MUTED);
        subtitleTv.setPadding(0, dp(context, 5), dp(context, 40), 0);
        titleBlock.addView(subtitleTv, matchW());

        header.addView(titleBlock, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        // Close button
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

        // ── Divider ──
        addDivider(context, card, 0);

        // ── Section: Lý do báo cáo ──
        TextView reasonsLabel = makeLabel(context, "Lý do báo cáo");
        reasonsLabel.setPadding(
                dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 10));
        card.addView(reasonsLabel, matchW());

        LinearLayout tilesWrap = new LinearLayout(context);
        tilesWrap.setOrientation(LinearLayout.VERTICAL);
        tilesWrap.setPadding(dp(context, 16), 0, dp(context, 16), dp(context, 16));

        for (int i = 0; i < REASONS.length; i++) {
            final int idx = i;
            if (i > 0) addSpacer(context, tilesWrap, 8);

            // Tile
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.HORIZONTAL);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(context, 14), dp(context, 13),
                    dp(context, 14), dp(context, 13));
            tile.setClickable(true);
            tile.setFocusable(true);
            tile.setBackground(makeTileBg(false, context));
            tile.setClipToOutline(true);

            // Radio indicator
            View radio = new View(context);
            radio.setBackground(makeRadioBg(false, context));
            int radioSz = dp(context, 20);
            tile.addView(radio, new LinearLayout.LayoutParams(radioSz, radioSz));

            // Label
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
                // Deselect previous
                int prev = selectedIndex[0];
                if (prev >= 0 && prev != idx) {
                    tileRefs[prev].setBackground(makeTileBg(false, context));
                    radioRefs[prev].setBackground(makeRadioBg(false, context));
                    textRefs[prev].setTextColor(COLOR_TEXT);
                    textRefs[prev].setTypeface(null, Typeface.NORMAL);
                }
                selectedIndex[0] = idx;
                tile.setBackground(makeTileBg(true, context));
                radio.setBackground(makeRadioBg(true, context));
                reasonTv.setTextColor(COLOR_BLUE);
                reasonTv.setTypeface(null, Typeface.BOLD);

                boolean isOther = isOtherReason(REASONS[idx]);
                if (otherReasonSectionRef[0] != null) {
                    otherReasonSectionRef[0].setVisibility(isOther ? View.VISIBLE : View.GONE);
                }
                if (descRef[0] != null) {
                    descRef[0].setHint("Nhập lý do khác...");
                    descRef[0].setError(null);
                    if (!isOther) {
                        descRef[0].setText("");
                    }
                }
                updateSubmitState(submitRef[0], selectedIndex[0], descRef[0]);
            });

            tilesWrap.addView(tile, matchW());
        }
        card.addView(tilesWrap, matchW());

        // ── Divider ──
        addDivider(context, card, 0);

        // ── Section: Lý do khác, chỉ hiện khi chọn "Lý do khác" ──
        LinearLayout otherReasonSection = new LinearLayout(context);
        otherReasonSection.setOrientation(LinearLayout.VERTICAL);
        otherReasonSection.setVisibility(View.GONE);
        otherReasonSectionRef[0] = otherReasonSection;

        TextView descLabel = makeLabel(context, "Nhập lý do khác");
        descLabel.setPadding(
                dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 8));
        otherReasonSection.addView(descLabel, matchW());

        LinearLayout descWrap = new LinearLayout(context);
        descWrap.setOrientation(LinearLayout.VERTICAL);
        descWrap.setPadding(dp(context, 16), 0, dp(context, 16), 0);

        EditText descInput = new EditText(context);
        descRef[0] = descInput;
        descInput.setHint("Nhập lý do khác...");
        descInput.setTextSize(15);
        descInput.setTextColor(COLOR_TEXT);
        descInput.setMinLines(3);
        descInput.setMaxLines(6);
        descInput.setPadding(
                dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        GradientDrawable descBg = new GradientDrawable();
        descBg.setColor(COLOR_SURFACE);
        descBg.setCornerRadius(dp(context, 10));
        descBg.setStroke(1, COLOR_SEPARATOR);
        descInput.setBackground(descBg);
        descInput.setGravity(Gravity.TOP | Gravity.START);
        descInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                if (descInput.getText() != null && descInput.getText().toString().trim().length() > 0) {
                    descInput.setError(null);
                }
                updateSubmitState(submitRef[0], selectedIndex[0], descInput);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        descWrap.addView(descInput, matchW());

        TextView descHint = new TextView(context);
        descHint.setText("Bắt buộc khi chọn 'Lý do khác'");
        descHint.setTextSize(12);
        descHint.setTextColor(COLOR_MUTED);
        descHint.setPadding(dp(context, 4), dp(context, 6), 0, 0);
        descWrap.addView(descHint, matchW());

        otherReasonSection.addView(descWrap, matchW());
        card.addView(otherReasonSection, matchW());

        // ── Divider ──
        addDivider(context, card, 0);

        // ── Section: Hình ảnh bằng chứng ──
        LinearLayout imgSection = new LinearLayout(context);
        imgSection.setOrientation(LinearLayout.VERTICAL);
        imgSection.setPadding(
                dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 6));

        // Header row: label + add button
        LinearLayout imgHeaderRow = new LinearLayout(context);
        imgHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        imgHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView imgLabel = makeLabel(context, "Hình ảnh bằng chứng");
        imgHeaderRow.addView(imgLabel,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView addImgBtn = new TextView(context);
        addImgBtn.setText("+ Thêm ảnh");
        addImgBtn.setTextSize(14);
        addImgBtn.setTextColor(COLOR_BLUE);
        addImgBtn.setPadding(dp(context, 6), dp(context, 2), 0, dp(context, 2));
        addImgBtn.setClickable(true);
        addImgBtn.setFocusable(true);
        android.util.TypedValue rplAdd = new android.util.TypedValue();
        context.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, rplAdd, true);
        addImgBtn.setBackgroundResource(rplAdd.resourceId);
        addImgBtn.setOnClickListener(v -> {
            if (selectedImages.size() >= MAX_IMAGES) {
                Toast.makeText(context,
                        "Tối đa " + MAX_IMAGES + " ảnh", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                PickerFragment.pick(context, uris -> {
                    int remaining = MAX_IMAGES - selectedImages.size();
                    int toAdd = Math.min(uris.size(), remaining);
                    for (int i = 0; i < toAdd; i++) selectedImages.add(uris.get(i));
                    if (uris.size() > remaining) {
                        Toast.makeText(context,
                                "Chỉ thêm được " + remaining + " ảnh nữa",
                                Toast.LENGTH_SHORT).show();
                    }
                    refreshThumbs(context, thumbRef[0], selectedImages,
                            submitRef[0], selectedIndex, descRef[0]);
                });
            } catch (Exception e) {
                Toast.makeText(context,
                        "Không thể mở thư viện ảnh", Toast.LENGTH_SHORT).show();
            }
        });
        imgHeaderRow.addView(addImgBtn);
        imgSection.addView(imgHeaderRow, matchW());

        TextView imgSubtitle = new TextView(context);
        imgSubtitle.setText("Bạn có thể đính kèm hình ảnh nếu có");
        imgSubtitle.setTextSize(12);
        imgSubtitle.setTextColor(COLOR_MUTED);
        imgSubtitle.setPadding(0, dp(context, 3), 0, 0);
        imgSection.addView(imgSubtitle, matchW());

        card.addView(imgSection, matchW());

        // Thumbnail horizontal scroll
        HorizontalScrollView hScroll = new HorizontalScrollView(context);
        hScroll.setBackgroundColor(0x00000000);
        hScroll.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), 0);
        hScroll.setClipToPadding(false);

        LinearLayout thumbContainer = new LinearLayout(context);
        thumbRef[0] = thumbContainer;
        thumbContainer.setOrientation(LinearLayout.HORIZONTAL);
        thumbContainer.setGravity(Gravity.CENTER_VERTICAL);
        hScroll.addView(thumbContainer, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        card.addView(hScroll, matchW());

        // Rules hint
        TextView imgHint = new TextView(context);
        imgHint.setText("Tối đa " + MAX_IMAGES + " ảnh  ·  jpg, png, webp  ·  mỗi ảnh tối đa 5MB");
        imgHint.setTextSize(11);
        imgHint.setTextColor(COLOR_MUTED);
        imgHint.setPadding(
                dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 16));
        card.addView(imgHint, matchW());

        // ── Divider ──
        addDivider(context, card, 0);

        // ── Footer ──
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(
                dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 24));

        // Primary CTA: Gửi báo cáo
        TextView submit = new TextView(context);
        submitRef[0] = submit;
        submit.setText("Gửi báo cáo");
        submit.setTextSize(16);
        submit.setTextColor(COLOR_WHITE);
        submit.setTypeface(null, Typeface.BOLD);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(
                dp(context, 16), dp(context, 15), dp(context, 16), dp(context, 15));
        submit.setEnabled(false);
        submit.setAlpha(0.45f);
        GradientDrawable submitBg = new GradientDrawable();
        submitBg.setColor(COLOR_BLUE);
        submitBg.setCornerRadius(dp(context, 12));
        submit.setBackground(submitBg);
        submit.setClickable(true);
        submit.setFocusable(true);
        submit.setOnClickListener(v -> {
            if (!submit.isEnabled()) return;
            String reason = REASONS[selectedIndex[0]];
            String desc   = descInput.getText() != null
                    ? descInput.getText().toString().trim() : "";
            if (isOtherReason(reason) && desc.isEmpty()) {
                descInput.setError("Vui lòng nhập lý do khác.");
                descInput.requestFocus();
                return;
            }
            submit.setEnabled(false);
            submit.setAlpha(0.45f);
            submit.setText("Đang gửi...");
            doSubmit(context, sheet, userId, reason, desc,
                    new ArrayList<>(selectedImages), submit, displayName);
        });
        footer.addView(submit, matchW());

        // Secondary: Hủy
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

        sheet.setContentView(scrollView);
        sheet.getBehavior().setSkipCollapsed(true);
        sheet.getBehavior().setState(
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.show();
    }

    // ── Thumbnail row ──

    private static void refreshThumbs(Context context, LinearLayout container,
                                       List<Uri> images, TextView submit,
                                       int[] selectedIndexRef, EditText descInput) {
        container.removeAllViews();
        int size = dp(context, 80);
        int gap  = dp(context, 8);

        for (int i = 0; i < images.size(); i++) {
            final int idx = i;
            Uri uri = images.get(i);

            FrameLayout frame = new FrameLayout(context);
            LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(size, size);
            frameLp.setMarginEnd(gap);
            frame.setLayoutParams(frameLp);

            ImageView imageView = new ImageView(context);
            imageView.setLayoutParams(new FrameLayout.LayoutParams(size, size));
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setClipToOutline(true);
            GradientDrawable imgBg = new GradientDrawable();
            imgBg.setColor(0xFFE5E5EA);
            imgBg.setCornerRadius(dp(context, 10));
            imageView.setBackground(imgBg);
            Glide.with(context).load(uri).centerCrop().into(imageView);
            frame.addView(imageView);

            TextView del = new TextView(context);
            del.setText("✕");
            del.setTextSize(10);
            del.setTextColor(0xFFFFFFFF);
            del.setGravity(Gravity.CENTER);
            del.setClickable(true);
            del.setFocusable(true);
            GradientDrawable delBg = new GradientDrawable();
            delBg.setShape(GradientDrawable.OVAL);
            delBg.setColor(0xCC000000);
            del.setBackground(delBg);
            int delSz = dp(context, 22);
            FrameLayout.LayoutParams delLp = new FrameLayout.LayoutParams(delSz, delSz);
            delLp.gravity    = Gravity.TOP | Gravity.END;
            delLp.topMargin  = dp(context, 3);
            delLp.rightMargin = dp(context, 3);
            del.setLayoutParams(delLp);
            // Bug #4 fix: dùng tham chiếu URI trực tiếp thay vì index.
            // idx bị capture lúc tạo view → xóa ảnh giữa dãy làm lệch index → xóa sai ảnh.
            final Uri uriRef = uri;
            del.setOnClickListener(v -> {
                images.remove(uriRef);
                refreshThumbs(context, container, images, submit, selectedIndexRef, descInput);
            });
            frame.addView(del);
            container.addView(frame);
        }

        updateSubmitState(submit, selectedIndexRef[0], descInput);
    }

    // ── Submit with sequential image upload ──

    private static void doSubmit(Context context, BottomSheetDialog sheet, long userId,
                                  String reason, String description,
                                  List<Uri> imageUris, TextView submit,
                                  String displayName) {
        RetrofitClient.loadToken(context);
        uploadImagesSequentially(context, imageUris, 0, new ArrayList<>(),
                urls -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("targetType", "USER");
                    body.put("targetId", userId);
                    body.put("reason", reason);
                    body.put("description", description);
                    if (!urls.isEmpty()) body.put("evidenceImages", urls);

                    RetrofitClient.getClient().create(ReportApiService.class)
                            .createReport(body)
                            .enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call,
                                                       Response<ApiResponse<Void>> response) {
                                    if (response.isSuccessful()) {
                                        sheet.dismiss();
                                        showBlockSuggestionDialog(context, userId, displayName);
                                    } else {
                                        resetSubmit(submit);
                                        Toast.makeText(context,
                                                "Không thể gửi báo cáo. Vui lòng thử lại.",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    resetSubmit(submit);
                                    Toast.makeText(context,
                                            "Không thể gửi báo cáo. Vui lòng thử lại.",
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                },
                () -> {
                    resetSubmit(submit);
                    Toast.makeText(context,
                            "Không thể upload ảnh. Vui lòng thử lại.",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private static void showBlockSuggestionDialog(Context context, long userId, String displayName) {
        if (context == null || userId <= 0) return;
        String safeName = displayName != null && !displayName.trim().isEmpty()
                ? displayName.trim()
                : "người dùng này";

        new MaterialAlertDialogBuilder(context)
                .setTitle("Đã gửi báo cáo")
                .setMessage("Cảm ơn bạn đã phản hồi. Bạn có muốn chặn " + safeName
                        + " để hạn chế tương tác và tin nhắn từ người này không?")
                .setNegativeButton("Để sau", null)
                .setPositiveButton("Chặn", (dialog, which) -> {
                    RetrofitClient.loadToken(context);
                    RetrofitClient.getClient()
                            .create(FriendApiService.class)
                            .blockUser(userId)
                            .enqueue(new Callback<ApiResponse<Void>>() {
                                @Override
                                public void onResponse(Call<ApiResponse<Void>> call,
                                                       Response<ApiResponse<Void>> response) {
                                    if (response.isSuccessful()) {
                                        Toast.makeText(context,
                                                "Đã chặn " + safeName,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context,
                                                "Không thể chặn người dùng",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                                    Toast.makeText(context,
                                            "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .show();
    }

    private static void uploadImagesSequentially(Context context, List<Uri> uris, int index,
                                                   List<String> urls,
                                                   Consumer<List<String>> onDone,
                                                   Runnable onError) {
        if (index >= uris.size()) {
            onDone.accept(urls);
            return;
        }
        Uri uri = uris.get(index);
        try {
            // Bug #2 fix: thay readBytes() (đọc toàn bộ file vào RAM, gây OOM với ảnh lớn)
            // bằng compressImageUri() — scale bằng inSampleSize trước khi load, nén JPEG 80%.
            byte[] bytes = compressImageUri(context, uri);
            if (bytes == null) {
                Toast.makeText(context,
                        "Không thể đọc ảnh " + (index + 1), Toast.LENGTH_SHORT).show();
                onError.run();
                return;
            }

            // Output của compressImageUri luôn là JPEG nén ~ ≤ 300KB
            RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), bytes);
            MultipartBody.Part part = MultipartBody.Part.createFormData(
                    "file", "evidence_" + index + ".jpg", requestBody);

            RetrofitClient.getClient().create(FileUploadApi.class)
                    .uploadImage(part)
                    .enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call,
                                               Response<ApiResponse<String>> response) {
                            if (response.isSuccessful() && response.body() != null
                                    && response.body().getResult() != null) {
                                urls.add(response.body().getResult());
                                uploadImagesSequentially(
                                        context, uris, index + 1, urls, onDone, onError);
                            } else {
                                onError.run();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            onError.run();
                        }
                    });
        } catch (Exception e) {
            onError.run();
        }
    }

    /**
     * Nén ảnh từ URI xuống dung lượng nhỏ trước khi upload.
     * Dùng BitmapFactory với inSampleSize để load ảnh theo dạng thu nhỏ,
     * tránh OOM khi gặp ảnh gốc kích thước lớn (RAW, HEIC, ảnh 50MP, v.v.).
     * Output: JPEG byte array, resize về max 1280px, quality 80%, ~ 100-300KB.
     * Trả về null nếu xảy ra bất kỳ lỗi nào (caller hiển thị Toast, không crash).
     */
    private static byte[] compressImageUri(Context context, Uri uri) {
        try {
            ContentResolver cr = context.getContentResolver();

            // Bước 1: Đọc CHỈ kích thước ảnh, không load pixel vào RAM
            BitmapFactory.Options sizeOpts = new BitmapFactory.Options();
            sizeOpts.inJustDecodeBounds = true;
            InputStream probe = cr.openInputStream(uri);
            if (probe == null) return null;
            BitmapFactory.decodeStream(probe, null, sizeOpts);
            probe.close();

            // Bước 2: Tính hệ số giảm mẫu để ảnh vừa trong max 1280x1280
            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = calculateInSampleSize(sizeOpts, 1280, 1280);
            // RGB_565 giảm bộ nhớ 50% so với ARGB_8888 (đủ cho ảnh bằng chứng)
            decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;

            InputStream is = cr.openInputStream(uri);
            if (is == null) return null;
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, decodeOpts);
            is.close();

            if (bitmap == null) return null;

            // Bước 3: Nén thành JPEG 80% → đủ chất lượng làm bằng chứng, kích thước nhỏ
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
            bitmap.recycle();

            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    // Tính inSampleSize (lũy thừa 2) để ảnh gốc thu vừa maxW × maxH
    private static int calculateInSampleSize(BitmapFactory.Options options, int maxW, int maxH) {
        int inSampleSize = 1;
        if (options.outHeight > maxH || options.outWidth > maxW) {
            int halfH = options.outHeight / 2;
            int halfW = options.outWidth / 2;
            while ((halfH / inSampleSize) >= maxH && (halfW / inSampleSize) >= maxW) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static void resetSubmit(TextView submit) {
        submit.setEnabled(true);
        submit.setAlpha(1.0f);
        submit.setText("Gửi báo cáo");
    }

    // ── State helpers ──

    private static void updateSubmitState(TextView submit, int selectedIndex, EditText descInput) {
        if (submit == null) return;
        boolean enabled = selectedIndex >= 0;
        if (enabled && isOtherReason(REASONS[selectedIndex])) {
            String text = descInput != null && descInput.getText() != null
                    ? descInput.getText().toString().trim() : "";
            enabled = !text.isEmpty();
        }
        submit.setEnabled(enabled);
        submit.setAlpha(enabled ? 1.0f : 0.45f);
    }

    private static boolean isOtherReason(String reason) {
        return "Lý do khác".equalsIgnoreCase(reason) || "Khác".equalsIgnoreCase(reason);
    }

    // ── Drawable builders ──

    private static GradientDrawable makeTileBg(boolean selected, Context ctx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(ctx, 10));
        if (selected) {
            gd.setColor(COLOR_BLUE_BG);
            gd.setStroke(dp(ctx, 2), COLOR_BLUE);
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
            gd.setColor(COLOR_BLUE);
        } else {
            gd.setColor(COLOR_WHITE);
            gd.setStroke(dp(ctx, 2), COLOR_SEPARATOR);
        }
        return gd;
    }

    // ── Layout helpers ──

    private static TextView makeLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(COLOR_MUTED);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.04f);
        return tv;
    }

    private static void addDivider(Context context, LinearLayout parent, int topMarginDp) {
        View div = new View(context);
        div.setBackgroundColor(COLOR_SEPARATOR);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        if (topMarginDp > 0) lp.topMargin = dp(context, topMarginDp);
        parent.addView(div, lp);
    }

    private static void addSpacer(Context context, LinearLayout parent, int heightDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, heightDp)));
        parent.addView(spacer);
    }

    private static LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
