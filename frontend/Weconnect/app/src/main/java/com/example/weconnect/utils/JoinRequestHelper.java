package com.example.weconnect.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.data.AdministrativeLocationData;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.JoinGroupResponse;
import com.example.weconnect.models.Post;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class JoinRequestHelper {
    private static final String SAME_LOCAL_JOIN_REASON = "Cùng địa phương";
    private static final int MIN_REMOTE_JOIN_REASON_LENGTH = 10;
    private static final int MAX_REMOTE_JOIN_REASON_LENGTH = 500;
    private static final int COLOR_PRIMARY = Color.rgb(255, 75, 115);
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(34, 34, 34);
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(102, 102, 102);
    private static final int COLOR_TEXT_MUTED = Color.rgb(119, 119, 119);
    private static final int COLOR_BORDER = Color.rgb(229, 229, 229);
    private static final int COLOR_LOCATION_BG = Color.rgb(255, 247, 249);
    private static final int COLOR_CANCEL_BG = Color.rgb(244, 244, 244);

    private JoinRequestHelper() {}

    public interface JoinCallback {
        void onSending();
        void onSuccess(JoinGroupResponse result);
        void onError(String errorMessage);
    }

    public static void startJoinFlow(Context context, Post post, JoinCallback callback) {
        if (context == null || post == null) return;

        long postId;
        try {
            postId = Long.parseLong(post.getId());
        } catch (Exception e) {
            notifyError(callback, "Lỗi ID bài viết");
            return;
        }

        RetrofitClient.loadToken(context);
        if (RetrofitClient.getAuthToken() == null) {
            notifyError(callback, "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            return;
        }

        UserApiService userApi = RetrofitClient.getClient().create(UserApiService.class);
        userApi.getMyProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().getResult() == null) {
                    notifyError(callback, "Không tải được thông tin địa phương của bạn. Vui lòng thử lại.");
                    return;
                }

                Map<String, Object> profile = response.body().getResult();
                String currentUserCity = resolveUserProvince(profile);
                String postCity = extractPostCity(post);

                // Điều kiện biên: user cũ hoặc bài viết cũ có thể thiếu tỉnh/thành.
                // Khi thiếu dữ liệu, vẫn mở dialog để user nhập lý do thay vì gửi yêu cầu âm thầm.
                if (!hasText(currentUserCity) || !hasText(postCity)) {
                    showDifferentProvinceJoinDialog(
                            context,
                            displayCity(currentUserCity),
                            displayCity(postCity),
                            reason -> sendJoinRequestToServer(context, postId, reason,
                                    displayCity(currentUserCity), displayCity(postCity), true, callback)
                    );
                    return;
                }

                if (currentUserCity.equalsIgnoreCase(postCity)) {
                    sendJoinRequestToServer(context, postId, SAME_LOCAL_JOIN_REASON,
                            currentUserCity, postCity, false, callback);
                } else {
                    showDifferentProvinceJoinDialog(
                            context,
                            currentUserCity,
                            postCity,
                            reason -> sendJoinRequestToServer(context, postId, reason,
                                    currentUserCity, postCity, true, callback)
                    );
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                notifyError(callback, "Lỗi kết nối khi tải thông tin địa phương.");
            }
        });
    }

    private interface ReasonSubmitCallback {
        void onSubmit(String reason);
    }

    private static void showDifferentProvinceJoinDialog(Context context,
                                                        String currentUserCity,
                                                        String postCity,
                                                        ReasonSubmitCallback submitCallback) {
        String currentUserDisplay = formatProvinceForDialog(currentUserCity);
        String postDisplay = formatProvinceForDialog(postCity);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpPx(context, 20), dpPx(context, 20), dpPx(context, 20), dpPx(context, 20));
        container.setBackground(AppDialogHelper.dialogBackground(context));
        container.setElevation(dpPx(context, 8));
        scrollView.addView(container, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView warningIcon = new TextView(context);
        warningIcon.setText("⚠️");
        warningIcon.setTextSize(24);
        warningIcon.setGravity(Gravity.CENTER);
        warningIcon.setIncludeFontPadding(false);
        container.addView(warningIcon, matchWrapParams(0, 0, 0, 10));

        TextView titleView = new TextView(context);
        titleView.setText("Lưu ý về địa điểm tổ chức");
        titleView.setTextColor(COLOR_TEXT_PRIMARY);
        titleView.setTextSize(18);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        titleView.setIncludeFontPadding(false);
        container.addView(titleView, matchWrapParams(0, 0, 0, 12));

        TextView messageView = new TextView(context);
        messageView.setText("Bạn và người tổ chức đang ở hai vị trí khá xa nhau. "
                + "Hãy kiểm tra kỹ địa điểm trước khi gửi yêu cầu tham gia hoạt động này.");
        messageView.setTextColor(COLOR_TEXT_SECONDARY);
        messageView.setTextSize(14);
        messageView.setGravity(Gravity.START);
        messageView.setLineSpacing(dpPx(context, 2), 1.0f);
        container.addView(messageView, matchWrapParams(0, 0, 0, 16));

        LinearLayout locationBlock = new LinearLayout(context);
        locationBlock.setOrientation(LinearLayout.VERTICAL);
        locationBlock.setPadding(dpPx(context, 12), dpPx(context, 12), dpPx(context, 12), dpPx(context, 12));
        locationBlock.setBackground(roundedRect(COLOR_LOCATION_BG, dpPx(context, 14)));

        TextView userLocationView = new TextView(context);
        userLocationView.setText("📍 Bạn đang ở: " + currentUserDisplay);
        userLocationView.setTextColor(COLOR_TEXT_PRIMARY);
        userLocationView.setTextSize(13);
        userLocationView.setLineSpacing(dpPx(context, 2), 1.0f);
        locationBlock.addView(userLocationView, matchWrapParams(0, 0, 0, 8));

        TextView postLocationView = new TextView(context);
        postLocationView.setText("📌 Hoạt động tại: " + postDisplay);
        postLocationView.setTextColor(COLOR_TEXT_PRIMARY);
        postLocationView.setTextSize(13);
        postLocationView.setLineSpacing(dpPx(context, 2), 1.0f);
        locationBlock.addView(postLocationView, matchWrapParams(0, 0, 0, 0));

        container.addView(locationBlock, matchWrapParams(0, 0, 0, 18));

        TextView inputLabel = new TextView(context);
        inputLabel.setText("Lý do muốn tham gia");
        inputLabel.setTextColor(COLOR_TEXT_PRIMARY);
        inputLabel.setTextSize(14);
        inputLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        inputLabel.setIncludeFontPadding(false);
        container.addView(inputLabel, matchWrapParams(0, 0, 0, 8));

        TextInputLayout reasonLayout = new TextInputLayout(context);
        reasonLayout.setHintEnabled(false);
        reasonLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        reasonLayout.setBoxBackgroundColor(Color.WHITE);
        reasonLayout.setBoxCornerRadii(
                dpPx(context, 12),
                dpPx(context, 12),
                dpPx(context, 12),
                dpPx(context, 12)
        );
        reasonLayout.setBoxStrokeColorStateList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_focused},
                        new int[]{}
                },
                new int[]{
                        COLOR_PRIMARY,
                        COLOR_BORDER
                }
        ));

        TextInputEditText reasonInput = new TextInputEditText(reasonLayout.getContext());
        reasonInput.setHint("Nhập lý do bạn muốn tham gia hoạt động này...");
        reasonInput.setHintTextColor(Color.rgb(150, 150, 150));
        reasonInput.setTextColor(COLOR_TEXT_PRIMARY);
        reasonInput.setTextSize(14);
        reasonInput.setMinLines(2);
        reasonInput.setMaxLines(4);
        reasonInput.setMinHeight(dpPx(context, 96));
        reasonInput.setGravity(Gravity.TOP | Gravity.START);
        reasonInput.setSingleLine(false);
        reasonInput.setPadding(dpPx(context, 12), dpPx(context, 12), dpPx(context, 12), dpPx(context, 12));
        reasonInput.setLineSpacing(dpPx(context, 2), 1.0f);
        reasonInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_REMOTE_JOIN_REASON_LENGTH)});
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonLayout.addView(reasonInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        container.addView(reasonLayout, matchWrapParams(0, 0, 0, 4));

        TextView counterView = new TextView(context);
        counterView.setText("0/" + MAX_REMOTE_JOIN_REASON_LENGTH);
        counterView.setTextColor(COLOR_TEXT_MUTED);
        counterView.setTextSize(12);
        counterView.setGravity(Gravity.END);
        container.addView(counterView, matchWrapParams(0, 0, 0, 18));

        LinearLayout buttonRow = new LinearLayout(context);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView cancelButton = new TextView(context);
        cancelButton.setText("Hủy");
        cancelButton.setTextSize(15);
        cancelButton.setTextColor(AppDialogHelper.COLOR_PRIMARY);
        cancelButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setIncludeFontPadding(false);
        cancelButton.setMinHeight(dpPx(context, 52));
        cancelButton.setPadding(dpPx(context, 16), 0, dpPx(context, 16), 0);
        cancelButton.setBackground(AppDialogHelper.secondaryButtonBackground(context));
        cancelButton.setClickable(true);
        cancelButton.setFocusable(true);

        TextView confirmButton = new TextView(context);
        confirmButton.setText("Xác nhận gửi");
        confirmButton.setTextSize(15);
        confirmButton.setTextColor(Color.WHITE);
        confirmButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        confirmButton.setGravity(Gravity.CENTER);
        confirmButton.setIncludeFontPadding(false);
        confirmButton.setMinHeight(dpPx(context, 52));
        confirmButton.setPadding(dpPx(context, 16), 0, dpPx(context, 16), 0);
        confirmButton.setBackground(AppDialogHelper.primaryButtonBackground(context));
        confirmButton.setClickable(true);
        confirmButton.setFocusable(true);

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0,
                dpPx(context, 52),
                2f
        );
        cancelParams.setMargins(0, 0, dpPx(context, 12), 0);
        buttonRow.addView(cancelButton, cancelParams);

        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0,
                dpPx(context, 52),
                3f
        );
        buttonRow.addView(confirmButton, confirmParams);
        container.addView(buttonRow, matchWrapParams(0, 0, 0, 0));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(scrollView)
                .create();

        reasonInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentLength = s == null ? 0 : s.length();
                counterView.setText(currentLength + "/" + MAX_REMOTE_JOIN_REASON_LENGTH);
                String reason = s == null ? "" : s.toString().trim();
                if (reason.length() >= MIN_REMOTE_JOIN_REASON_LENGTH || reason.length() == 0) {
                    reasonLayout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        cancelButton.setOnClickListener(v -> dialog.dismiss());

        confirmButton.setOnClickListener(v -> {
            String userReason = reasonInput.getText() != null
                    ? reasonInput.getText().toString().trim()
                    : "";

            if (userReason.isEmpty()) {
                reasonLayout.setError("Vui lòng nhập lý do tham gia.");
                return;
            }

            if (userReason.length() < MIN_REMOTE_JOIN_REASON_LENGTH) {
                reasonLayout.setError("Lý do cần có ít nhất 10 ký tự.");
                return;
            }

            submitCallback.onSubmit(userReason);
            dialog.dismiss();
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            int width = Math.round(context.getResources().getDisplayMetrics().widthPixels * 0.90f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void sendJoinRequestToServer(Context context,
                                                long postId,
                                                String joinReason,
                                                String requesterProvince,
                                                String activityProvince,
                                                boolean isFarLocation,
                                                JoinCallback callback) {
        if (callback != null) {
            callback.onSending();
        }

        RetrofitClient.loadToken(context);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);
        Map<String, Object> body = new HashMap<>();
        body.put("joinReason", joinReason);
        body.put("requesterProvince", requesterProvince != null ? requesterProvince : "");
        body.put("activityProvince", activityProvince != null ? activityProvince : "");
        body.put("isFarLocation", isFarLocation);

        postApi.joinPost(postId, body).enqueue(new Callback<ApiResponse<JoinGroupResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<JoinGroupResponse>> call,
                                   Response<ApiResponse<JoinGroupResponse>> response) {
                if (response.isSuccessful()) {
                    JoinGroupResponse result = response.body() != null ? response.body().getResult() : null;
                    if (callback != null) {
                        callback.onSuccess(result);
                    }
                    return;
                }

                String errorMsg = "Không thể tham gia. Thử lại.";
                try {
                    if (response.errorBody() != null) {
                        String body = response.errorBody().string();
                        org.json.JSONObject json = new org.json.JSONObject(body);
                        if (json.has("message")) errorMsg = json.getString("message");
                    }
                } catch (Exception ignored) {}
                notifyError(callback, errorMsg);
            }

            @Override
            public void onFailure(Call<ApiResponse<JoinGroupResponse>> call, Throwable t) {
                notifyError(callback, "Lỗi kết nối. Thử lại!");
            }
        });
    }

    private static String extractPostCity(Post post) {
        if (post == null || !hasText(post.getLocation())) return "";
        String location = post.getLocation().trim();
        String cityFromFullLocation = resolveProvinceName(location);

        // Bài viết có thể lưu cả quận/phường, nên cần dò cả chuỗi trước.
        if (isOfficialProvinceName(cityFromFullLocation)) {
            return cityFromFullLocation;
        }

        String[] parts = location.split(",");
        String lastPart = parts.length == 0 ? location : parts[parts.length - 1].trim();
        return resolveProvinceName(lastPart);
    }

    private static String resolveUserProvince(Map<String, Object> profile) {
        if (profile == null) return "";

        String provinceName = asString(profile.get("provinceName"));
        if (hasText(provinceName)) {
            return resolveProvinceName(provinceName);
        }

        String city = asString(profile.get("city"));
        if (hasText(city)) {
            return resolveProvinceName(city);
        }

        return resolveProvinceNameById(asString(profile.get("provinceId")));
    }

    private static String resolveProvinceNameById(String provinceId) {
        if (!hasText(provinceId)) return "";
        for (AdministrativeLocationData.Province province : AdministrativeLocationData.provinces()) {
            if (provinceId.trim().equalsIgnoreCase(province.id)) {
                return province.name;
            }
        }
        return "";
    }

    private static String resolveProvinceName(String rawLocation) {
        if (!hasText(rawLocation)) return "";
        String normalizedLocation = normalizeLocationText(rawLocation);

        // Điều kiện biên cho dữ liệu cũ: TP.HCM có thể được lưu bằng nhiều dạng viết tắt.
        if (normalizedLocation.contains("tp.hcm")
                || normalizedLocation.contains("tp hcm")
                || normalizedLocation.contains("tphcm")
                || normalizedLocation.contains("ho chi minh")
                || normalizedLocation.contains("sai gon")
                || normalizedLocation.contains("saigon")) {
            return "Thành phố Hồ Chí Minh";
        }

        for (AdministrativeLocationData.Province province : AdministrativeLocationData.provinces()) {
            String normalizedProvince = normalizeLocationText(province.name);
            if (normalizedLocation.equals(normalizedProvince)
                    || normalizedLocation.contains(normalizedProvince)
                    || normalizedProvince.contains(normalizedLocation)) {
                return province.name;
            }
        }

        return rawLocation.trim();
    }

    private static boolean isOfficialProvinceName(String value) {
        if (!hasText(value)) return false;
        for (AdministrativeLocationData.Province province : AdministrativeLocationData.provinces()) {
            if (province.name.equalsIgnoreCase(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String displayCity(String value) {
        return hasText(value) ? value.trim() : "chưa xác định";
    }

    private static String formatProvinceForDialog(String value) {
        if (!hasText(value)) return "chưa xác định";
        String trimmed = value.trim();
        if ("Thành phố Hồ Chí Minh".equalsIgnoreCase(trimmed)) {
            return "TP. Hồ Chí Minh";
        }
        return trimmed;
    }

    private static String normalizeLocationText(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    private static int dpPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrapParams(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static GradientDrawable roundedRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    private static void notifyError(JoinCallback callback, String errorMessage) {
        if (callback != null) {
            callback.onError(errorMessage);
        }
    }

    public static void showJoinToast(Context context, JoinGroupResponse result) {
        String baseMsg = (result != null && result.getMessage() != null && !result.getMessage().isEmpty())
                ? result.getMessage()
                : "Đã gửi yêu cầu tham gia!";
        boolean learnedNewTag = result != null && result.isNewTagSuggested();
        String toastMessage = learnedNewTag
                ? baseMsg + " WeConnect đã tự động ghi nhận chủ đề mới này để ưu tiên gợi ý lên trang chủ của bạn từ lần sau."
                : baseMsg;
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show();
    }
}
