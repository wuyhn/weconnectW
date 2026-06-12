package com.example.weconnect.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.adapters.WardSearchAdapter;
import com.example.weconnect.utils.AppDialogHelper;
import com.example.weconnect.utils.ProvinceWardLoader;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.utils.InterestTextUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;

public class CreatePostActivity extends AppCompatActivity {

    private EditText etPostContent;
    private TextView tvUserName;
    private ImageView ivClose, ivAddImage, ivAddLocation, ivTagInterest, ivUserAvatar;
    private MaterialButton btnPost;
    private String selectedTag = "";
    private MaterialCardView cardSelectedTag;
    private TextView tvSelectedTag;
    private ImageView ivParticipants;
    private MaterialCardView cardParticipantLimit;
    private TextView tvParticipantLimit;
    private int participantLimit = 0;
    private MaterialCardView cardSelectedLocation;
    private TextView tvSelectedLocation;
    private String selectedLocation = "";
    private String selectedCity = "";
    private ImageView ivPostImagePreview;
    private Uri selectedImageUri = null;
    private String editServerImageUrl = null; // server URL of existing post image in edit mode

    // Activity time (start date+time, end date+time — expiry auto = actEnd)
    // Lưu ý: "Thời hạn bài viết" picker đã được loại bỏ.
    // endTime luôn được đồng bộ với activityEndTime (thời điểm kết thúc hoạt động thực tế).
    private ImageView ivActivityTime;
    private MaterialCardView cardSelectedActivityTime;
    private TextView tvSelectedActivityTime;
    private boolean hasActivityTime = false;
    private int activityStartYear, activityStartMonth, activityStartDay;
    private int activityEndYear, activityEndMonth, activityEndDay;
    private int activityStartHour, activityStartMinute;
    private int activityEndHour, activityEndMinute;
    private String selectedActivityTimeType = "DAILY_TIME_SLOT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        ivClose = findViewById(R.id.ivClose);
        etPostContent = findViewById(R.id.etPostContent);
        tvUserName = findViewById(R.id.tvUserName);
        ivUserAvatar = findViewById(R.id.ivUserAvatar);
        // Hiển thị tên user thật
        String savedName = RetrofitClient.getUserName(this);
        if (savedName != null && !savedName.isEmpty()) {
            tvUserName.setText(savedName);
        }
        btnPost = findViewById(R.id.btnPost);
        ivAddImage = findViewById(R.id.ivAddImage);
        ivAddLocation = findViewById(R.id.ivAddLocation);
        ivTagInterest = findViewById(R.id.ivTagInterest);
        cardSelectedTag = findViewById(R.id.cardSelectedTag);
        tvSelectedTag = findViewById(R.id.tvSelectedTag);
        cardSelectedLocation = findViewById(R.id.cardSelectedLocation);
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation);
        ivParticipants = findViewById(R.id.ivParticipants);
        cardParticipantLimit = findViewById(R.id.cardParticipantLimit);
        tvParticipantLimit = findViewById(R.id.tvParticipantLimit);

        // Activity time views
        ivActivityTime = findViewById(R.id.ivActivityTime);
        cardSelectedActivityTime = findViewById(R.id.cardSelectedActivityTime);
        tvSelectedActivityTime = findViewById(R.id.tvSelectedActivityTime);

        ivClose.setOnClickListener(v -> finish());

        // Post button with validation
        btnPost.setOnClickListener(v -> handlePost());

        ivPostImagePreview = findViewById(R.id.ivPostImagePreview);

        // Image picker launcher
        ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            // Lấy quyền đọc URI vĩnh viễn để tránh SecurityException
                            try {
                                getContentResolver().takePersistableUriPermission(
                                        selectedImageUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException e) {
                                // Ignore nếu không hỗ trợ persistable
                            }
                            if (ivPostImagePreview != null) {
                                ivPostImagePreview.setImageURI(selectedImageUri);
                                ivPostImagePreview.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                }
        );

        ivAddImage.setOnClickListener(v -> {
            Intent pickImage = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pickImage.addCategory(Intent.CATEGORY_OPENABLE);
            pickImage.setType("image/*");
            pickImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            imagePickerLauncher.launch(pickImage);
        });
        ivAddLocation.setOnClickListener(v -> showLocationDialog());
        ivTagInterest.setOnClickListener(v -> showTagDialog());
        ivParticipants.setOnClickListener(v -> showParticipantDialog());

        // ivDuration đã bị loại bỏ — không còn picker "Thời hạn bài viết" riêng biệt.
        // endTime được tự động lấy từ activityEndTime do người dùng chọn.
        ivActivityTime.setOnClickListener(v -> showActivityTimeDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit();
            }
        });

        // === Edit mode: pre-fill fields ===
        if (getIntent().getBooleanExtra("edit_mode", false)) {
            btnPost.setText("Cập nhật");

            String editContent = getIntent().getStringExtra("edit_content");
            if (editContent != null) etPostContent.setText(editContent);

            String editTag = getIntent().getStringExtra("edit_tag");
            if (editTag != null && !editTag.isEmpty()) {
                selectedTag = editTag;
                tvSelectedTag.setText(InterestTextUtils.stripLeadingIcon(selectedTag));
                cardSelectedTag.setVisibility(View.VISIBLE);
            }

            String editLocation = getIntent().getStringExtra("edit_location");
            if (editLocation != null && !editLocation.isEmpty()) {
                selectedLocation = editLocation;
                tvSelectedLocation.setText("📍 " + selectedLocation);
                cardSelectedLocation.setVisibility(View.VISIBLE);
            }

            int editMaxMembers = getIntent().getIntExtra("edit_max_members", 0);
            if (editMaxMembers > 0) {
                participantLimit = editMaxMembers;
                tvParticipantLimit.setText("👥 Giới hạn: " + participantLimit + " người");
                cardParticipantLimit.setVisibility(View.VISIBLE);
            }

            // Duration is auto-calculated from activity end time — no pre-fill needed

            String editActivityTimeType = getIntent().getStringExtra("edit_activity_time_type");
            if (editActivityTimeType != null && !editActivityTimeType.isEmpty()) {
                selectedActivityTimeType = editActivityTimeType;
            }

            String editActivityStartIso = getIntent().getStringExtra("edit_activity_start_iso");
            String editActivityEndIso = getIntent().getStringExtra("edit_activity_end_iso");
            if (editActivityStartIso != null && !editActivityStartIso.isEmpty()
                    && editActivityEndIso != null && !editActivityEndIso.isEmpty()) {
                try {
                    java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
                    java.util.Calendar sc = java.util.Calendar.getInstance();
                    sc.setTime(isoFmt.parse(editActivityStartIso));
                    java.util.Calendar ec = java.util.Calendar.getInstance();
                    ec.setTime(isoFmt.parse(editActivityEndIso));
                    activityStartYear = sc.get(java.util.Calendar.YEAR);
                    activityStartMonth = sc.get(java.util.Calendar.MONTH);
                    activityStartDay = sc.get(java.util.Calendar.DAY_OF_MONTH);
                    activityStartHour = sc.get(java.util.Calendar.HOUR_OF_DAY);
                    activityStartMinute = sc.get(java.util.Calendar.MINUTE);
                    activityEndYear = ec.get(java.util.Calendar.YEAR);
                    activityEndMonth = ec.get(java.util.Calendar.MONTH);
                    activityEndDay = ec.get(java.util.Calendar.DAY_OF_MONTH);
                    activityEndHour = ec.get(java.util.Calendar.HOUR_OF_DAY);
                    activityEndMinute = ec.get(java.util.Calendar.MINUTE);
                    hasActivityTime = true;
                    String startDateStr = String.format("%02d/%02d/%04d", activityStartDay, activityStartMonth + 1, activityStartYear);
                    String endDateStr = String.format("%02d/%02d/%04d", activityEndDay, activityEndMonth + 1, activityEndYear);
                    String timeStr = String.format("%02d:%02d - %02d:%02d", activityStartHour, activityStartMinute, activityEndHour, activityEndMinute);
                    String display = startDateStr.equals(endDateStr)
                            ? "📅 " + startDateStr + "  🕐 " + timeStr
                            : "📅 " + startDateStr + " → " + endDateStr + "  🕐 " + timeStr;
                    tvSelectedActivityTime.setText(display);
                    cardSelectedActivityTime.setVisibility(View.VISIBLE);
                } catch (Exception ignored) {}
            }

            String editImageUri = getIntent().getStringExtra("edit_image_uri");
            if (editImageUri != null && !editImageUri.isEmpty() && ivPostImagePreview != null) {
                ivPostImagePreview.setVisibility(View.VISIBLE);
                if (editImageUri.startsWith("/") || editImageUri.startsWith("http")) {
                    // Server-hosted image: build full URL and load via Glide
                    String fullUrl = editImageUri.startsWith("/")
                            ? RetrofitClient.getBaseUrl() + editImageUri.substring(1)
                            : editImageUri;
                    editServerImageUrl = fullUrl;
                    com.bumptech.glide.Glide.with(this)
                            .load(fullUrl)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .into(ivPostImagePreview);
                } else {
                    // Local content:// URI
                    selectedImageUri = Uri.parse(editImageUri);
                    com.bumptech.glide.Glide.with(this)
                            .load(selectedImageUri)
                            .placeholder(R.drawable.ic_user_placeholder)
                            .error(R.drawable.ic_user_placeholder)
                            .into(ivPostImagePreview);
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ivUserAvatar == null) return;
        String avatarUrl = RetrofitClient.getAvatarUrl(this);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(ivUserAvatar);
        } else {
            ivUserAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }
    }

    private void handlePost() {
        String content = etPostContent.getText().toString().trim();

        // Validation
        if (content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập nội dung bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedTag.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn sở thích cho bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLocation.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn địa điểm", Toast.LENGTH_SHORT).show();
            return;
        }
        if (participantLimit <= 0) {
            Toast.makeText(this, "Vui lòng chọn giới hạn người tham gia", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasActivityTime) {
            Toast.makeText(this, "Vui lòng chọn thời gian hoạt động", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.Calendar actStartCal = java.util.Calendar.getInstance();
        actStartCal.set(activityStartYear, activityStartMonth, activityStartDay, activityStartHour, activityStartMinute, 0);
        actStartCal.set(java.util.Calendar.MILLISECOND, 0);

        java.util.Calendar nowTruncated = java.util.Calendar.getInstance();
        nowTruncated.set(java.util.Calendar.SECOND, 0);
        nowTruncated.set(java.util.Calendar.MILLISECOND, 0);
        if (actStartCal.before(nowTruncated)) {
            Toast.makeText(this, "Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại.", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.Calendar actEndCal = java.util.Calendar.getInstance();
        actEndCal.set(activityEndYear, activityEndMonth, activityEndDay, activityEndHour, activityEndMinute, 0);
        actEndCal.set(java.util.Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        long actEndMillis = actEndCal.getTimeInMillis();

        // Validate: activity has not already ended
        if (actEndMillis <= now) {
            Toast.makeText(this, "Hoạt động đã kết thúc, không thể đăng bài", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate: end > start (should already be enforced in picker, but double-check)
        if (!actEndCal.after(actStartCal)) {
            Toast.makeText(this, "Thời gian kết thúc phải sau thời gian bắt đầu hoạt động", Toast.LENGTH_SHORT).show();
            return;
        }

        // Post expiry = activity end time (auto-calculated)
        java.text.SimpleDateFormat isoFmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault());
        String activityStartIso = isoFmt.format(actStartCal.getTime());
        String activityEndIso = isoFmt.format(actEndCal.getTime());

        checkLocationAndPost(activityStartIso, activityEndIso, actEndMillis);
    }

    private static String stripProvincePrefix(String name) {
        if (name == null) return "";
        String s = name.trim();
        for (String prefix : new String[]{"Thành phố ", "thành phố ", "Tỉnh ", "tỉnh "}) {
            if (s.startsWith(prefix)) return s.substring(prefix.length());
        }
        return s;
    }

    private void checkLocationAndPost(String activityStartIso, String activityEndIso, long actEndMillis) {
        String userCity = RetrofitClient.getUserCity(this);

        if (userCity.isEmpty() || selectedCity.isEmpty()
                || stripProvincePrefix(userCity).equalsIgnoreCase(stripProvincePrefix(selectedCity))) {
            doSubmitPost(activityStartIso, activityEndIso, actEndMillis);
            return;
        }

        AppDialogHelper.showConfirm(
                this,
                "Lưu ý về địa điểm hoạt động",
                "Vị trí hiện tại của bạn (" + userCity + ") khác với địa điểm tổ chức hoạt động ("
                        + selectedCity + "). Bạn có chắc chắn muốn hoạt động này diễn ra tại " + selectedCity + " không?",
                "Xác nhận đăng",
                (dialog, which) -> doSubmitPost(activityStartIso, activityEndIso, actEndMillis),
                "Sửa lại"
        );
    }

    private void doSubmitPost(String activityStartIso, String activityEndIso, long actEndMillis) {
        Intent result = new Intent();
        result.putExtra("post_content", etPostContent.getText().toString().trim());
        result.putExtra("post_username", tvUserName.getText().toString());
        result.putExtra("post_time", "Vừa xong");
        result.putExtra("post_tag", selectedTag);
        result.putExtra("post_max_members", participantLimit);
        result.putExtra("post_location", selectedLocation);
        result.putExtra("post_end_time", actEndMillis);
        result.putExtra("post_activity_start_iso", activityStartIso);
        result.putExtra("post_activity_end_iso", activityEndIso);
        result.putExtra("post_activity_time_type", selectedActivityTimeType);
        if (selectedImageUri != null) {
            result.putExtra("post_image_uri", selectedImageUri.toString());
        } else if (editServerImageUrl != null) {
            result.putExtra("post_image_uri", editServerImageUrl);
        }
        long editPostId = getIntent().getLongExtra("edit_post_id", -1);
        if (editPostId != -1) {
            result.putExtra("edit_post_id", editPostId);
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private void handleExit() {
        if (!etPostContent.getText().toString().trim().isEmpty()) {
            AppDialogHelper.showConfirm(
                    this,
                    "Hủy bài viết?",
                    "Nội dung sẽ không được lưu.",
                    "Hủy bỏ",
                    (d, w) -> finish(),
                    "Viết tiếp"
            );
        } else {
            finish();
        }
    }

    private void showActivityTimeDialog() {
        BottomSheetDialog typeSheet = new BottomSheetDialog(this);
        typeSheet.getBehavior().setSkipCollapsed(true);

        android.widget.LinearLayout outer = new android.widget.LinearLayout(this);
        outer.setOrientation(android.widget.LinearLayout.VERTICAL);
        outer.setPadding(dpPx(16), dpPx(8), dpPx(16), dpPx(24));
        outer.setBackgroundColor(0xFFFFFFFF);

        // Handle bar
        View handle = new View(this);
        android.widget.LinearLayout.LayoutParams hlp =
                new android.widget.LinearLayout.LayoutParams(dpPx(40), dpPx(4));
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dpPx(12);
        hlp.bottomMargin = dpPx(16);
        handle.setLayoutParams(hlp);
        handle.setBackgroundColor(0xFFD1D1D6);
        outer.addView(handle);

        // Title
        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText("Loại thời gian hoạt động");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(0xFF1C1C1E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(0, 0, 0, dpPx(16));
        outer.addView(tvTitle, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        MaterialCardView card1 = buildTypeOptionCard(
                "🔄 Diễn ra mỗi ngày",
                "Khung giờ cố định, lặp lại mỗi ngày trong khoảng ngày đã chọn",
                "DAILY_TIME_SLOT".equals(selectedActivityTimeType));
        card1.setOnClickListener(v -> {
            typeSheet.dismiss();
            selectedActivityTimeType = "DAILY_TIME_SLOT";
            showDateTimePickers();
        });
        outer.addView(card1, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        View gap = new View(this);
        gap.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dpPx(8)));
        outer.addView(gap);

        MaterialCardView card2 = buildTypeOptionCard(
                "🗓️ Diễn ra một lần",
                "Hoạt động có thời điểm bắt đầu và kết thúc cụ thể",
                "CONTINUOUS_RANGE".equals(selectedActivityTimeType));
        card2.setOnClickListener(v -> {
            typeSheet.dismiss();
            selectedActivityTimeType = "CONTINUOUS_RANGE";
            showDateTimePickers();
        });
        outer.addView(card2, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        typeSheet.setContentView(outer);
        typeSheet.setOnShowListener(di -> {
            android.widget.FrameLayout bs = ((BottomSheetDialog) di)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bs.setBackground(bg);
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bs)
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        typeSheet.show();
    }

    private MaterialCardView buildTypeOptionCard(String title, String subtitle, boolean selected) {
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(selected ? 0xFFFFF0F3 : 0xFFF8F8F8);
        card.setRadius(dpPx(14));
        card.setCardElevation(0f);
        card.setStrokeWidth(selected ? dpPx(2) : 0);
        card.setStrokeColor(0xFFFF4D6D);
        card.setClickable(true);
        card.setFocusable(true);

        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        content.setPadding(dpPx(16), dpPx(14), dpPx(16), dpPx(14));

        android.widget.TextView tvT = new android.widget.TextView(this);
        tvT.setText(title);
        tvT.setTextSize(15);
        tvT.setTextColor(0xFF1C1C1E);
        tvT.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(tvT);

        android.widget.TextView tvS = new android.widget.TextView(this);
        tvS.setText(subtitle);
        tvS.setTextSize(13);
        tvS.setTextColor(0xFF8E8E93);
        tvS.setPadding(0, dpPx(4), 0, 0);
        content.addView(tvS);

        card.addView(content);
        return card;
    }

    // ── Dispatch: chọn flow theo loại ──────────────────────────────────────────
    private void showDateTimePickers() {
        if ("CONTINUOUS_RANGE".equals(selectedActivityTimeType)) {
            showContinuousStartDatePicker();
        } else {
            showDailyStartDatePicker();
        }
    }

    // ── Helpers chung ─────────────────────────────────────────────────────────

    /** Trả về true nếu startDatetime (year/month/day/hour/min) < thời gian hiện tại (so sánh theo phút). */
    private boolean isStartInPast(int year, int month, int day, int hour, int min) {
        java.util.Calendar startCal = java.util.Calendar.getInstance();
        startCal.set(year, month, day, hour, min, 0);
        startCal.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.set(java.util.Calendar.SECOND, 0);
        now.set(java.util.Calendar.MILLISECOND, 0);
        return startCal.before(now);
    }

    private void updateActivityTimeDisplay() {
        String startDateStr = String.format("%02d/%02d/%04d", activityStartDay, activityStartMonth + 1, activityStartYear);
        String endDateStr   = String.format("%02d/%02d/%04d", activityEndDay,   activityEndMonth   + 1, activityEndYear);
        String display;
        if ("CONTINUOUS_RANGE".equals(selectedActivityTimeType)) {
            display = "🟢 " + startDateStr + " " + String.format("%02d:%02d", activityStartHour, activityStartMinute)
                    + "  🔴 " + endDateStr + " " + String.format("%02d:%02d", activityEndHour, activityEndMinute);
        } else {
            String timeStr = String.format("%02d:%02d - %02d:%02d",
                    activityStartHour, activityStartMinute, activityEndHour, activityEndMinute);
            display = startDateStr.equals(endDateStr)
                    ? "📅 " + startDateStr + "  ⏰ Mỗi ngày: " + timeStr
                    : "📅 " + startDateStr + " - " + endDateStr + "  ⏰ Mỗi ngày: " + timeStr;
        }
        tvSelectedActivityTime.setText(display);
        cardSelectedActivityTime.setVisibility(View.VISIBLE);
    }

    // ── DAILY_TIME_SLOT flow: ngày bắt đầu → ngày kết thúc → giờ bắt đầu → giờ kết thúc ──

    private void showDailyStartDatePicker() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int initY = hasActivityTime ? activityStartYear  : cal.get(java.util.Calendar.YEAR);
        int initM = hasActivityTime ? activityStartMonth : cal.get(java.util.Calendar.MONTH);
        int initD = hasActivityTime ? activityStartDay   : cal.get(java.util.Calendar.DAY_OF_MONTH);

        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this,
                (v, sYear, sMonth, sDay) -> showDailyEndDatePicker(sYear, sMonth, sDay),
                initY, initM, initD);
        dlg.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dlg.setTitle("Ngày bắt đầu hoạt động");
        dlg.show();
    }

    private void showDailyEndDatePicker(int sYear, int sMonth, int sDay) {
        int initY = hasActivityTime ? activityEndYear  : sYear;
        int initM = hasActivityTime ? activityEndMonth : sMonth;
        int initD = hasActivityTime ? activityEndDay   : sDay;

        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this,
                (v, eYear, eMonth, eDay) -> {
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    int initH = hasActivityTime ? activityStartHour   : cal.get(java.util.Calendar.HOUR_OF_DAY);
                    int initMin = hasActivityTime ? activityStartMinute : 0;
                    showDailyStartTimePicker(sYear, sMonth, sDay, eYear, eMonth, eDay, initH, initMin);
                },
                initY, initM, initD);
        // Ngày kết thúc >= ngày bắt đầu
        java.util.Calendar minEnd = java.util.Calendar.getInstance();
        minEnd.set(sYear, sMonth, sDay, 0, 0, 0);
        dlg.getDatePicker().setMinDate(minEnd.getTimeInMillis());
        dlg.setTitle("Ngày kết thúc hoạt động");
        dlg.show();
    }

    private void showDailyStartTimePicker(int sYear, int sMonth, int sDay,
                                          int eYear, int eMonth, int eDay,
                                          int initH, int initMin) {
        android.app.TimePickerDialog dlg = new android.app.TimePickerDialog(this,
                (v, sHour, sMin) -> {
                    if (isStartInPast(sYear, sMonth, sDay, sHour, sMin)) {
                        Toast.makeText(this, "Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại.", Toast.LENGTH_SHORT).show();
                        showDailyStartTimePicker(sYear, sMonth, sDay, eYear, eMonth, eDay, sHour, sMin);
                        return;
                    }
                    int initEH = hasActivityTime ? activityEndHour   : (sHour + 1) % 24;
                    int initEM = hasActivityTime ? activityEndMinute : 0;
                    showDailyEndTimePicker(sYear, sMonth, sDay, eYear, eMonth, eDay, sHour, sMin, initEH, initEM);
                },
                initH, initMin, true);
        dlg.setTitle("Giờ bắt đầu (mỗi ngày)");
        dlg.show();
    }

    private void showDailyEndTimePicker(int sYear, int sMonth, int sDay,
                                        int eYear, int eMonth, int eDay,
                                        int sHour, int sMin,
                                        int initH, int initMin) {
        android.app.TimePickerDialog dlg = new android.app.TimePickerDialog(this,
                (v, eHour, eMin) -> {
                    if (eHour < sHour || (eHour == sHour && eMin <= sMin)) {
                        Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu hoạt động.", Toast.LENGTH_SHORT).show();
                        showDailyEndTimePicker(sYear, sMonth, sDay, eYear, eMonth, eDay, sHour, sMin, eHour, eMin);
                        return;
                    }
                    activityStartYear = sYear; activityStartMonth = sMonth; activityStartDay = sDay;
                    activityStartHour = sHour; activityStartMinute = sMin;
                    activityEndYear = eYear; activityEndMonth = eMonth; activityEndDay = eDay;
                    activityEndHour = eHour; activityEndMinute = eMin;
                    hasActivityTime = true;
                    updateActivityTimeDisplay();
                },
                initH, initMin, true);
        dlg.setTitle("Giờ kết thúc (mỗi ngày)");
        dlg.show();
    }

    // ── CONTINUOUS_RANGE flow: ngày bắt đầu → giờ bắt đầu → ngày kết thúc → giờ kết thúc ──

    private void showContinuousStartDatePicker() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int initY = hasActivityTime ? activityStartYear  : cal.get(java.util.Calendar.YEAR);
        int initM = hasActivityTime ? activityStartMonth : cal.get(java.util.Calendar.MONTH);
        int initD = hasActivityTime ? activityStartDay   : cal.get(java.util.Calendar.DAY_OF_MONTH);

        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this,
                (v, sYear, sMonth, sDay) -> {
                    java.util.Calendar c = java.util.Calendar.getInstance();
                    int initH   = hasActivityTime ? activityStartHour   : c.get(java.util.Calendar.HOUR_OF_DAY);
                    int initMin = hasActivityTime ? activityStartMinute : 0;
                    showContinuousStartTimePicker(sYear, sMonth, sDay, initH, initMin);
                },
                initY, initM, initD);
        dlg.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dlg.setTitle("Ngày bắt đầu hoạt động");
        dlg.show();
    }

    private void showContinuousStartTimePicker(int sYear, int sMonth, int sDay,
                                               int initH, int initMin) {
        android.app.TimePickerDialog dlg = new android.app.TimePickerDialog(this,
                (v, sHour, sMin) -> {
                    if (isStartInPast(sYear, sMonth, sDay, sHour, sMin)) {
                        Toast.makeText(this, "Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại.", Toast.LENGTH_SHORT).show();
                        showContinuousStartTimePicker(sYear, sMonth, sDay, sHour, sMin);
                        return;
                    }
                    int initEY = hasActivityTime ? activityEndYear  : sYear;
                    int initEM = hasActivityTime ? activityEndMonth : sMonth;
                    int initED = hasActivityTime ? activityEndDay   : sDay;
                    showContinuousEndDatePicker(sYear, sMonth, sDay, sHour, sMin, initEY, initEM, initED);
                },
                initH, initMin, true);
        dlg.setTitle("Giờ bắt đầu hoạt động");
        dlg.show();
    }

    private void showContinuousEndDatePicker(int sYear, int sMonth, int sDay, int sHour, int sMin,
                                             int initEY, int initEM, int initED) {
        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(this,
                (v, eYear, eMonth, eDay) -> {
                    int initEH  = hasActivityTime ? activityEndHour   : (sHour + 1) % 24;
                    int initEMin = hasActivityTime ? activityEndMinute : 0;
                    showContinuousEndTimePicker(sYear, sMonth, sDay, sHour, sMin,
                            eYear, eMonth, eDay, initEH, initEMin);
                },
                initEY, initEM, initED);
        // Ngày kết thúc >= ngày bắt đầu
        java.util.Calendar minEnd = java.util.Calendar.getInstance();
        minEnd.set(sYear, sMonth, sDay, 0, 0, 0);
        dlg.getDatePicker().setMinDate(minEnd.getTimeInMillis());
        dlg.setTitle("Ngày kết thúc hoạt động");
        dlg.show();
    }

    private void showContinuousEndTimePicker(int sYear, int sMonth, int sDay, int sHour, int sMin,
                                             int eYear, int eMonth, int eDay,
                                             int initH, int initMin) {
        android.app.TimePickerDialog dlg = new android.app.TimePickerDialog(this,
                (v, eHour, eMin) -> {
                    java.util.Calendar startCal = java.util.Calendar.getInstance();
                    startCal.set(sYear, sMonth, sDay, sHour, sMin, 0);
                    java.util.Calendar endCal = java.util.Calendar.getInstance();
                    endCal.set(eYear, eMonth, eDay, eHour, eMin, 0);
                    if (!endCal.after(startCal)) {
                        Toast.makeText(this, "Thời gian kết thúc phải sau thời gian bắt đầu.", Toast.LENGTH_SHORT).show();
                        showContinuousEndTimePicker(sYear, sMonth, sDay, sHour, sMin,
                                eYear, eMonth, eDay, eHour, eMin);
                        return;
                    }
                    activityStartYear = sYear; activityStartMonth = sMonth; activityStartDay = sDay;
                    activityStartHour = sHour; activityStartMinute = sMin;
                    activityEndYear = eYear; activityEndMonth = eMonth; activityEndDay = eDay;
                    activityEndHour = eHour; activityEndMinute = eMin;
                    hasActivityTime = true;
                    updateActivityTimeDisplay();
                },
                initH, initMin, true);
        dlg.setTitle("Giờ kết thúc hoạt động");
        dlg.show();
    }

    // ── Danh sách 60 tag cứng dùng làm fallback khi mất mạng ────────────────
    // Đồng bộ với TagController.SYSTEM_TAGS trên backend.
    // Khi backend thêm/sửa tag, cần cập nhật cả list này.
    private static final java.util.List<String> FALLBACK_SYSTEM_TAGS = java.util.Arrays.asList(
            "⚽ Đá bóng sân cỏ", "🏸 Đánh cầu lông", "🏀 Đánh bóng rổ", "🏐 Đánh bóng chuyền",
            "🏃 Chạy bộ công viên", "🚴 Đạp xe đường phố", "🏊 Đi bơi hồ", "🎾 Đánh Pickleball",
            "🛹 Trượt ván / Patin", "🧗 Leo núi nhân tạo", "🧘 Tập Yoga / Pilates", "🏋️ Tập Gym / Calisthenics",
            "🎬 Xem phim rạp", "🎵 Đi nghe nhạc / Concert", "🎤 Đi hát Karaoke",
            "🎮 Chơi Game (PC/Console)", "📱 Chơi Mobile Game / Liên Quân", "🎲 Chơi Board game / Ma soi",
            "📸 Đi chụp ảnh / Check-in", "🎨 Vẽ tranh thư giãn", "💃 Học nhảy / Vũ đạo",
            "🎭 Xem kịch / Xem Stand-up Comedy",
            "☕ Học nhóm / Chạy deadline", "📖 Đọc sách tại thư viện", "🌍 Luyện nói tiếng Anh",
            "🎌 Học tiếng Nhật / Trung / Hàn", "💻 Lập trình dự án / Hackathon",
            "📐 Thiết kế đồ họa / UI-UX", "📝 Ôn thi / Giải đề",
            "💼 Thảo luận ý tưởng khởi nghiệp", "🔬 Làm thí nghiệm / Nghiên cứu",
            "🤖 Lập trình AI / Học Data Science",
            "☕ Đi Cafe cà pháo", "🍜 Đi Foodtour / Ăn sập phố cổ", "🍽️ Food Tour",
            "✈️ Đi du lịch xa / Phượt", "🏕️ Đi cắm trại / Camping", "🌿 Đi dạo công viên / Picnic",
            "🧗 Leo núi tự nhiên / Trekking", "🐕 Đi offline giao lưu thú cưng",
            "🎪 Làm tình nguyện / Từ thiện", "🛍️ Đi mua sắm / Shopping", "🎣 Đi câu cá thư giãn",
            "💬 Trò chuyện tâm sự / Hướng nội", "🍻 Nhậu nhẹt / Chill cuối tuần",
            "🎸 Tập chơi nhạc cụ (Guitar/Piano)", "🧩 Xếp hình Lego / Giải Rubik",
            "✍️ Viết lách / Viết Blog", "🎬 Quay Video / Làm Tiktok",
            "🔮 Xem bài Tarot / Chiêm tinh", "🍳 Tụ tập nấu ăn / Làm bánh",
            "🪴 Trồng cây / Làm vườn", "🪡 Thêu thùa / Làm đồ thủ công",
            "♟️ Đánh cờ vua / Cờ tướng", "🎤 Tập nói trước đám đông / Debate",
            "💸 Học quản lý tài chính cá nhân", "🚗 Tập lái xe / Trải nghiệm xe",
            "🎯 Chơi bắn cung / Phi tiêu", "🎳 Chơi Bowling",
            "🎈 Tham gia lễ hội / Fandom", "🧩 Đi giải mật phòng (Escape Room)"
    );

    // Cấu trúc 6 nhóm tag để hiển thị theo danh mục trong BottomSheet.
    // Phải đồng bộ với TagController.SYSTEM_TAGS (backend) và OnboardingActivity (Android).
    // Khi thêm/sửa tag: cập nhật TagController → OnboardingActivity → đây (theo thứ tự đó).
    private static final java.util.LinkedHashMap<String, String[]> TAG_CATEGORIES;
    static {
        TAG_CATEGORIES = new java.util.LinkedHashMap<>();
        TAG_CATEGORIES.put("⚽ Thể thao", new String[]{
                "⚽ Đá bóng sân cỏ", "🏸 Đánh cầu lông", "🏀 Đánh bóng rổ", "🏐 Đánh bóng chuyền",
                "🏃 Chạy bộ công viên", "🚴 Đạp xe đường phố", "🏊 Đi bơi hồ", "🎾 Đánh Pickleball",
                "🛹 Trượt ván / Patin", "🧗 Leo núi nhân tạo", "🧘 Tập Yoga / Pilates", "🏋️ Tập Gym / Calisthenics"
        });
        TAG_CATEGORIES.put("🎬 Giải trí", new String[]{
                "🎬 Xem phim rạp", "🎵 Đi nghe nhạc / Concert", "🎤 Đi hát Karaoke",
                "🎮 Chơi Game (PC/Console)", "📱 Chơi Mobile Game / Liên Quân", "🎲 Chơi Board game / Ma soi",
                "📸 Đi chụp ảnh / Check-in", "🎨 Vẽ tranh thư giãn", "💃 Học nhảy / Vũ đạo",
                "🎭 Xem kịch / Xem Stand-up Comedy"
        });
        TAG_CATEGORIES.put("📚 Học tập & Công nghệ", new String[]{
                "☕ Học nhóm / Chạy deadline", "📖 Đọc sách tại thư viện", "🌍 Luyện nói tiếng Anh",
                "🎌 Học tiếng Nhật / Trung / Hàn", "💻 Lập trình dự án / Hackathon",
                "📐 Thiết kế đồ họa / UI-UX", "📝 Ôn thi / Giải đề",
                "💼 Thảo luận ý tưởng khởi nghiệp", "🔬 Làm thí nghiệm / Nghiên cứu",
                "🤖 Lập trình AI / Học Data Science"
        });
        TAG_CATEGORIES.put("🌿 Đời sống & Du lịch", new String[]{
                "☕ Đi Cafe cà pháo", "🍜 Đi Foodtour / Ăn sập phố cổ", "🍽️ Food Tour",
                "✈️ Đi du lịch xa / Phượt", "🏕️ Đi cắm trại / Camping", "🌿 Đi dạo công viên / Picnic",
                "🧗 Leo núi tự nhiên / Trekking", "🐕 Đi offline giao lưu thú cưng",
                "🎪 Làm tình nguyện / Từ thiện", "🛍️ Đi mua sắm / Shopping", "🎣 Đi câu cá thư giãn"
        });
        TAG_CATEGORIES.put("💬 Giao lưu & Hobby", new String[]{
                "💬 Trò chuyện tâm sự / Hướng nội", "🍻 Nhậu nhẹt / Chill cuối tuần",
                "🎸 Tập chơi nhạc cụ (Guitar/Piano)", "🧩 Xếp hình Lego / Giải Rubik",
                "✍️ Viết lách / Viết Blog", "🎬 Quay Video / Làm Tiktok",
                "🔮 Xem bài Tarot / Chiêm tinh", "🍳 Tụ tập nấu ăn / Làm bánh",
                "🪴 Trồng cây / Làm vườn", "🪡 Thêu thùa / Làm đồ thủ công"
        });
        TAG_CATEGORIES.put("✨ Xu hướng & Kỹ năng", new String[]{
                "♟️ Đánh cờ vua / Cờ tướng", "🎤 Tập nói trước đám đông / Debate",
                "💸 Học quản lý tài chính cá nhân", "🚗 Tập lái xe / Trải nghiệm xe",
                "🎯 Chơi bắn cung / Phi tiêu", "🎳 Chơi Bowling",
                "🎈 Tham gia lễ hội / Fandom", "🧩 Đi giải mật phòng (Escape Room)"
        });
    }

    /**
     * Mở BottomSheet chọn tag cho bài viết.
     *
     * Luồng mới:
     *   1. Gọi GET /api/tags/all → lấy toàn bộ 60 tag hệ thống (không giới hạn theo sở thích cá nhân)
     *   2. Nếu thành công  → hiển thị danh sách từ server (luôn up-to-date)
     *   3. Nếu mạng lỗi   → dùng FALLBACK_SYSTEM_TAGS cứng trong code (không toast lỗi, không chặn user)
     *
     * Lý do bỏ logic cũ (getInterests + SharedPreferences fallback):
     *   - Logic cũ chỉ hiển thị ≤5 tag cá nhân → quá hạn chế, user không thể chọn tag ngoài sở thích
     *   - Nếu user chưa thiết lập sở thích → toast lỗi chặn hoàn toàn, không đăng bài được
     */
    private void showTagDialog() {
        RetrofitClient.loadToken(this);
        com.example.weconnect.api.TagApiService tagApi =
                RetrofitClient.getClient().create(com.example.weconnect.api.TagApiService.class);

        tagApi.getAllSystemTags().enqueue(
                new retrofit2.Callback<com.example.weconnect.models.ApiResponse<java.util.List<String>>>() {
                    @Override
                    public void onResponse(
                            retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call,
                            retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.List<String>>> response) {

                        java.util.List<String> tags = null;
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null
                                && !response.body().getResult().isEmpty()) {
                            tags = response.body().getResult();
                        }
                        // Fallback sang list cứng nếu backend không trả dữ liệu hợp lệ
                        showTagBottomSheet(tags != null ? tags : FALLBACK_SYSTEM_TAGS);
                    }

                    @Override
                    public void onFailure(
                            retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call,
                            Throwable t) {
                        // Mất mạng → dùng danh sách cứng, không block user
                        showTagBottomSheet(FALLBACK_SYSTEM_TAGS);
                    }
                });
    }

    /**
     * Hiển thị BottomSheet chọn tag phân theo 6 danh mục với thanh tìm kiếm lọc động.
     *
     * Cấu trúc UI:
     *   handle bar → title → divider → search bar → divider
     *   → ScrollView:
     *       [Header nhóm]  ← ẩn khi tất cả chip trong nhóm bị filter
     *       [ChipGroup]
     *       ... (lặp lại cho 6 nhóm)
     *   → Nút Xác nhận
     *
     * Single-selection được quản lý thủ công qua selectedChipRef[] vì có nhiều
     * ChipGroup riêng biệt — ChipGroup.setSingleSelection() chỉ hoạt động nội bộ 1 group.
     *
     * Khi user gõ tìm kiếm:
     *   - Chip không khớp → GONE + bỏ check nếu đang được chọn
     *   - Header nhóm → GONE nếu tất cả chip trong nhóm đó đều GONE
     */
    private void showTagBottomSheet(java.util.List<String> allTags) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        // ── Outer wrapper ────────────────────────────────────────────────────
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(0x00000000);

        // ── Handle bar trang trí ─────────────────────────────────────────────
        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dpPx(40), dpPx(4));
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dpPx(12);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundColor(0xFFD1D1D6);
        outer.addView(handle);

        // ── Tiêu đề ──────────────────────────────────────────────────────────
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Chọn tag hoạt động");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(0xFF1C1C1E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(dpPx(16), dpPx(14), dpPx(16), dpPx(12));
        outer.addView(tvTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ── Divider ──────────────────────────────────────────────────────────
        View divider1 = new View(this);
        divider1.setBackgroundColor(0xFFF2F2F7);
        divider1.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(1)));
        outer.addView(divider1);

        // ── Ô tìm kiếm ───────────────────────────────────────────────────────
        android.widget.EditText etSearch = new android.widget.EditText(this);
        etSearch.setHint("🔍  Tìm tag...");
        etSearch.setHintTextColor(0xFFAEAEB2);
        etSearch.setTextColor(0xFF1C1C1E);
        etSearch.setTextSize(14);
        etSearch.setSingleLine(true);
        etSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        etSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        etSearch.setBackgroundResource(android.R.color.transparent);
        etSearch.setPadding(dpPx(16), dpPx(12), dpPx(16), dpPx(12));

        android.widget.FrameLayout searchCard = new android.widget.FrameLayout(this);
        android.graphics.drawable.GradientDrawable searchBg = new android.graphics.drawable.GradientDrawable();
        searchBg.setColor(0xFFF2F2F7);
        searchBg.setCornerRadius(dpPx(12));
        searchCard.setBackground(searchBg);
        searchCard.addView(etSearch, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchLp.setMargins(dpPx(16), dpPx(12), dpPx(16), dpPx(4));
        outer.addView(searchCard, searchLp);

        // ── Divider 2 ─────────────────────────────────────────────────────────
        View divider2 = new View(this);
        divider2.setBackgroundColor(0xFFF2F2F7);
        divider2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(1)));
        outer.addView(divider2);

        // ── ScrollView chứa nội dung phân danh mục ───────────────────────────
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setFillViewport(true);
        // Giới hạn 52% chiều cao màn hình → nút Xác nhận luôn visible phía dưới
        int maxScrollH = (int) (getResources().getDisplayMetrics().heightPixels * 0.52f);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, maxScrollH));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dpPx(16), dpPx(4), dpPx(16), dpPx(8));

        // ── Tracking cho filter và single-selection ───────────────────────────
        // allChips: flat list toàn bộ chip để duyệt khi filter
        final java.util.List<Chip> allChips = new java.util.ArrayList<>();
        // Parallel lists: sectionHeaders[i] tương ứng với sectionChips[i]
        final java.util.List<View> sectionHeaders = new java.util.ArrayList<>();
        final java.util.List<java.util.List<Chip>> sectionChips = new java.util.ArrayList<>();
        // Single-element array để capture ref trong lambda (Java không cho dùng non-final local)
        final Chip[] selectedChipRef = {null};

        // ── Xây dựng danh mục từ TAG_CATEGORIES ──────────────────────────────
        for (java.util.Map.Entry<String, String[]> entry : TAG_CATEGORIES.entrySet()) {

            // Header nhóm
            TextView tvHeader = new TextView(this);
            tvHeader.setText(entry.getKey());
            tvHeader.setTextSize(11.5f);
            tvHeader.setTextColor(0xFF8E8E93);
            tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerLp.setMargins(0, dpPx(16), 0, dpPx(6));
            tvHeader.setLayoutParams(headerLp);
            content.addView(tvHeader);

            // ChipGroup cho nhóm này — không dùng setSingleSelection, quản lý thủ công
            ChipGroup chipGroup = new ChipGroup(this);
            chipGroup.setChipSpacingHorizontal(dpPx(6));
            chipGroup.setChipSpacingVertical(dpPx(4));
            chipGroup.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            java.util.List<Chip> chipsInSection = new java.util.ArrayList<>();

            for (String tagFull : entry.getValue()) {
                String displayText = InterestTextUtils.stripLeadingIcon(tagFull);
                if (displayText.isEmpty()) continue;

                Chip chip = new Chip(this);
                chip.setText(displayText);
                chip.setTag(tagFull.trim()); // full string với emoji — dùng làm key khi submit
                chip.setCheckable(true);
                chip.setCheckedIconVisible(false);
                chip.setChipCornerRadius(dpPx(20));
                chip.setTextSize(13f);
                applyUnselectedChipStyle(chip); // style mặc định

                // Pre-select nếu tag này đang được chọn (reopen dialog / edit mode)
                if (!selectedTag.isEmpty() && tagFull.trim().equals(selectedTag)) {
                    chip.setChecked(true);
                    applySelectedChipStyle(chip);
                    selectedChipRef[0] = chip;
                }

                // Single-selection thủ công: bỏ chọn chip cũ khi chip mới được chọn
                chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        if (selectedChipRef[0] != null && selectedChipRef[0] != chip) {
                            final Chip prev = selectedChipRef[0];
                            // post() để tránh gọi setChecked trong listener của listener
                            chip.post(() -> prev.setChecked(false));
                        }
                        selectedChipRef[0] = chip;
                        applySelectedChipStyle(chip);
                    } else {
                        if (selectedChipRef[0] == chip) selectedChipRef[0] = null;
                        applyUnselectedChipStyle(chip);
                    }
                });

                chipGroup.addView(chip);
                chipsInSection.add(chip);
                allChips.add(chip);
            }

            content.addView(chipGroup);
            sectionHeaders.add(tvHeader);
            sectionChips.add(chipsInSection);
        }

        scrollView.addView(content);
        outer.addView(scrollView);

        // ── TextWatcher: lọc chip + ẩn/hiện header nhóm ─────────────────────
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String query = s.toString().trim().toLowerCase();

                for (Chip chip : allChips) {
                    boolean matches = query.isEmpty()
                            || chip.getText().toString().toLowerCase().contains(query);
                    chip.setVisibility(matches ? View.VISIBLE : View.GONE);
                    if (!matches && chip.isChecked()) chip.setChecked(false);
                }

                // Ẩn header nhóm khi tất cả chip thuộc nhóm đó đều bị filter ra
                for (int i = 0; i < sectionHeaders.size(); i++) {
                    boolean anyVisible = false;
                    for (Chip c : sectionChips.get(i)) {
                        if (c.getVisibility() == View.VISIBLE) { anyVisible = true; break; }
                    }
                    sectionHeaders.get(i).setVisibility(anyVisible ? View.VISIBLE : View.GONE);
                }
            }
        });

        // ── Nút Xác nhận ─────────────────────────────────────────────────────
        com.google.android.material.button.MaterialButton btnOk =
                new com.google.android.material.button.MaterialButton(this);
        btnOk.setText("Xác nhận");
        btnOk.setTextSize(16);
        btnOk.setAllCaps(false);
        btnOk.setCornerRadius(dpPx(26));
        btnOk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(52));
        btnLp.setMargins(dpPx(20), dpPx(16), dpPx(20), dpPx(24));
        btnOk.setLayoutParams(btnLp);

        btnOk.setOnClickListener(v -> {
            if (selectedChipRef[0] == null) {
                Toast.makeText(this, "Bạn chưa chọn tag nào!", Toast.LENGTH_SHORT).show();
                return;
            }
            Object raw = selectedChipRef[0].getTag();
            // Lưu full string (có emoji) — dùng để submit lên server và match feed
            selectedTag = raw != null ? raw.toString() : selectedChipRef[0].getText().toString();
            tvSelectedTag.setText(InterestTextUtils.stripLeadingIcon(selectedTag));
            cardSelectedTag.setVisibility(View.VISIBLE);
            dialog.dismiss();
        });
        outer.addView(btnOk);

        // ── Hiển thị BottomSheet ──────────────────────────────────────────────
        dialog.setContentView(outer);
        dialog.setOnShowListener(di -> {
            FrameLayout bs = ((BottomSheetDialog) di)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bs != null) {
                android.graphics.drawable.GradientDrawable bgSheet =
                        new android.graphics.drawable.GradientDrawable();
                bgSheet.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bgSheet.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bs.setBackground(bgSheet);
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(bs)
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    // Style chip khi được chọn: nền hồng nhạt + viền hồng + chữ hồng
    private void applySelectedChipStyle(Chip chip) {
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFFFF0F3));
        chip.setChipStrokeWidth(1.5f * getResources().getDisplayMetrics().density);
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFFF4D6D));
        chip.setTextColor(0xFFFF4D6D);
    }

    // Style chip khi không được chọn: nền xám nhạt + không viền + chữ đen nhạt
    private void applyUnselectedChipStyle(Chip chip) {
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF2F2F7));
        chip.setChipStrokeWidth(0f);
        chip.setTextColor(0xFF3A3A3C);
    }

    private void showParticipantDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_participant_limit, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = ((BottomSheetDialog) dialogInterface)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bottomSheet.setBackground(bg);

                int screenH = getResources().getDisplayMetrics().heightPixels;
                bottomSheet.getLayoutParams().height = (int)(screenH * 0.55f);
                bottomSheet.requestLayout();

                com.google.android.material.bottomsheet.BottomSheetBehavior behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ImageView ivCloseParticipant = view.findViewById(R.id.ivCloseParticipant);
        com.google.android.material.textfield.TextInputEditText etParticipantLimit =
                view.findViewById(R.id.etParticipantLimit);
        MaterialButton btnOkParticipant = view.findViewById(R.id.btnOkParticipant);

        Chip chipParticipant5 = view.findViewById(R.id.chipParticipant5);
        Chip chipParticipant10 = view.findViewById(R.id.chipParticipant10);
        Chip chipParticipant20 = view.findViewById(R.id.chipParticipant20);
        Chip chipParticipant50 = view.findViewById(R.id.chipParticipant50);

        ivCloseParticipant.setOnClickListener(v -> dialog.dismiss());

        if (participantLimit > 0) {
            etParticipantLimit.setText(String.valueOf(participantLimit));
        }

        chipParticipant5.setOnClickListener(v -> etParticipantLimit.setText("5"));
        chipParticipant10.setOnClickListener(v -> etParticipantLimit.setText("10"));
        chipParticipant20.setOnClickListener(v -> etParticipantLimit.setText("20"));
        chipParticipant50.setOnClickListener(v -> etParticipantLimit.setText("50"));

        btnOkParticipant.setOnClickListener(v -> {
            String input = etParticipantLimit.getText() != null
                    ? etParticipantLimit.getText().toString().trim()
                    : "";

            if (input.length() == 0) {
                Toast.makeText(this, "Ban chua nhap gioi han nguoi tham gia!", Toast.LENGTH_SHORT).show();
                return;
            }

            participantLimit = Integer.parseInt(input);
            tvParticipantLimit.setText("👥 Giới hạn: " + participantLimit + " người");
            cardParticipantLimit.setVisibility(View.VISIBLE);

            dialog.dismiss();
        });

        dialog.show();
    }

    private void showLocationDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_location_picker, null);
        dialog.setContentView(view);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = ((BottomSheetDialog) dialogInterface)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bottomSheet.setBackground(bg);

                int screenH = getResources().getDisplayMetrics().heightPixels;
                bottomSheet.getLayoutParams().height = (int)(screenH * 0.55f);
                bottomSheet.requestLayout();

                com.google.android.material.bottomsheet.BottomSheetBehavior behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        AutoCompleteTextView actCity = view.findViewById(R.id.actCity);
        AutoCompleteTextView actWard = view.findViewById(R.id.actWard);
        TextView tvLocationPreview = view.findViewById(R.id.tvLocationPreview);
        MaterialButton btnOkLocation = view.findViewById(R.id.btnOkLocation);

        // Load 34 tỉnh/thành + toàn bộ phường/xã từ assets/provinces_wards.json
        java.util.List<ProvinceWardLoader.Province> provinces = ProvinceWardLoader.load(this);
        java.util.List<String> provinceNames = ProvinceWardLoader.getProvinceNames(provinces);

        // Adapter tỉnh/thành — 34 mục, dropdown thuần
        android.widget.ArrayAdapter<String> cityAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, provinceNames);
        actCity.setAdapter(cityAdapter);
        actCity.setThreshold(0);

        // Khi chọn tỉnh/thành → nạp phường/xã tương ứng vào WardSearchAdapter
        final ProvinceWardLoader.Province[] selectedProvince = {null};
        actCity.setOnItemClickListener((parent, v, position, id) -> {
            actWard.setText("", false);
            selectedProvince[0] = ProvinceWardLoader.findByName(provinces, provinceNames.get(position));
            if (selectedProvince[0] != null) {
                WardSearchAdapter wardAdapter = new WardSearchAdapter(this, selectedProvince[0].wards);
                actWard.setAdapter(wardAdapter);
                actWard.setThreshold(0);
            }
            updateLocationPreview(actCity, actWard, tvLocationPreview);
        });

        actWard.setOnClickListener(v -> {
            if (actWard.getAdapter() != null) actWard.showDropDown();
        });
        actWard.setOnItemClickListener((parent, v, position, id) ->
                updateLocationPreview(actCity, actWard, tvLocationPreview));

        // Pre-fill nếu đã có địa điểm
        if (selectedLocation != null && !selectedLocation.isEmpty()) {
            tvLocationPreview.setText("📍 " + selectedLocation);
        }

        // Quick chips — điền sẵn tỉnh/thành phố + phường/xã
        Chip chipHaDong = view.findViewById(R.id.chipHaDong);
        Chip chipCauGiay = view.findViewById(R.id.chipCauGiay);
        Chip chipThuDuc = view.findViewById(R.id.chipThuDuc);
        Chip chipHaiChau = view.findViewById(R.id.chipHaiChau);

        chipHaDong.setOnClickListener(v -> applyQuickChip(
                actCity, actWard, tvLocationPreview, provinces, provinceNames,
                "Thành phố Hà Nội", "Phường Hà Đông", selectedProvince));
        chipCauGiay.setOnClickListener(v -> applyQuickChip(
                actCity, actWard, tvLocationPreview, provinces, provinceNames,
                "Thành phố Hà Nội", "Phường Cầu Giấy", selectedProvince));
        chipThuDuc.setOnClickListener(v -> applyQuickChip(
                actCity, actWard, tvLocationPreview, provinces, provinceNames,
                "Thành phố Hồ Chí Minh", "Phường Thủ Đức", selectedProvince));
        chipHaiChau.setOnClickListener(v -> applyQuickChip(
                actCity, actWard, tvLocationPreview, provinces, provinceNames,
                "Thành phố Đà Nẵng", "Phường Hải Châu", selectedProvince));

        // Xác nhận — yêu cầu cả tỉnh/thành lẫn phường/xã
        btnOkLocation.setOnClickListener(v -> {
            String ward = actWard.getText() != null ? actWard.getText().toString().trim() : "";
            String city = actCity.getText() != null ? actCity.getText().toString().trim() : "";

            if (city.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn tỉnh/thành phố.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ward.isEmpty()) {
                Toast.makeText(this, "Vui lòng chọn phường/xã.", Toast.LENGTH_SHORT).show();
                return;
            }

            selectedLocation = ward + ", " + city;
            selectedCity = city;
            tvSelectedLocation.setText("📍 " + selectedLocation);
            cardSelectedLocation.setVisibility(View.VISIBLE);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyQuickChip(
            AutoCompleteTextView actCity,
            AutoCompleteTextView actWard,
            TextView tvPreview,
            java.util.List<ProvinceWardLoader.Province> provinces,
            java.util.List<String> provinceNames,
            String provinceName,
            String wardName,
            ProvinceWardLoader.Province[] selectedProvince) {
        actCity.setText(provinceName, false);
        selectedProvince[0] = ProvinceWardLoader.findByName(provinces, provinceName);
        if (selectedProvince[0] != null) {
            WardSearchAdapter wardAdapter = new WardSearchAdapter(this, selectedProvince[0].wards);
            actWard.setAdapter(wardAdapter);
            actWard.setThreshold(0);
        }
        actWard.setText(wardName, false);
        updateLocationPreview(actCity, actWard, tvPreview);
    }

    private int dpPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateLocationPreview(
            AutoCompleteTextView actCity,
            AutoCompleteTextView actWard,
            TextView tvPreview) {
        String ward = actWard.getText() != null ? actWard.getText().toString().trim() : "";
        String city = actCity.getText() != null ? actCity.getText().toString().trim() : "";

        if (city.isEmpty() && ward.isEmpty()) {
            tvPreview.setText("📍 Chưa chọn địa điểm");
        } else if (ward.isEmpty()) {
            tvPreview.setText("📍 " + city + " · Chọn phường/xã");
        } else if (city.isEmpty()) {
            tvPreview.setText("📍 " + ward);
        } else {
            tvPreview.setText("📍 " + ward + ", " + city);
        }
    }
}
