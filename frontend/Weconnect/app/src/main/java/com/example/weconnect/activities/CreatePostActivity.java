package com.example.weconnect.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.weconnect.R;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.AdministrativeLocationData;
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
    private ImageView ivPostImagePreview;
    private Uri selectedImageUri = null;
    private String editServerImageUrl = null; // server URL of existing post image in edit mode

    // Duration
    private ImageView ivDuration;
    private MaterialCardView cardSelectedDuration;
    private TextView tvSelectedDuration;
    private long selectedDurationMillis = 0;
    private String selectedDurationLabel = "";

    // Activity time (start date+time, end date+time — expiry auto = actEnd)
    private ImageView ivActivityTime;
    private MaterialCardView cardSelectedActivityTime;
    private TextView tvSelectedActivityTime;
    private boolean hasActivityTime = false;
    private int activityStartYear, activityStartMonth, activityStartDay;
    private int activityEndYear, activityEndMonth, activityEndDay;
    private int activityStartHour, activityStartMinute;
    private int activityEndHour, activityEndMinute;
    private String selectedActivityTimeType = "DAILY_TIME_SLOT";

    private static final long ONE_HOUR = 60L * 60L * 1000L;
    private static final long ONE_DAY = 24L * ONE_HOUR;

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

        // Duration views
        ivDuration = findViewById(R.id.ivDuration);
        cardSelectedDuration = findViewById(R.id.cardSelectedDuration);
        tvSelectedDuration = findViewById(R.id.tvSelectedDuration);

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
        ivDuration.setOnClickListener(v -> showDurationDialog());
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
                tvSelectedTag.setText(selectedTag);
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

        Intent result = new Intent();
        result.putExtra("post_content", content);
        result.putExtra("post_username", tvUserName.getText().toString());
        result.putExtra("post_time", "Vừa xong");
        result.putExtra("post_tag", selectedTag);
        result.putExtra("post_max_members", participantLimit);
        result.putExtra("post_location", selectedLocation);
        result.putExtra("post_end_time", actEndMillis); // expiry = activity end
        result.putExtra("post_activity_start_iso", activityStartIso);
        result.putExtra("post_activity_end_iso", activityEndIso);
        result.putExtra("post_activity_time_type", selectedActivityTimeType);
        if (selectedImageUri != null) {
            result.putExtra("post_image_uri", selectedImageUri.toString());
        } else if (editServerImageUrl != null) {
            result.putExtra("post_image_uri", editServerImageUrl);
        }
        // Nếu đang ở edit mode, truyền lại post ID
        long editPostId = getIntent().getLongExtra("edit_post_id", -1);
        if (editPostId != -1) {
            result.putExtra("edit_post_id", editPostId);
        }
        setResult(RESULT_OK, result);
        finish();
    }

    private void handleExit() {
        if (!etPostContent.getText().toString().trim().isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Huỷ bài viết?")
                    .setMessage("Nội dung sẽ không được lưu.")
                    .setPositiveButton("Huỷ bỏ", (d, w) -> finish())
                    .setNegativeButton("Viết tiếp", null)
                    .show();
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

    private void showDurationDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        // Outer: handle bar + scrollable body
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(0x00000000);

        // Handle bar
        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dpPx(40), dpPx(4));
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dpPx(12);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundColor(0xFFD1D1D6);
        outer.addView(handle);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Thời hạn bài viết");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(0xFF1C1C1E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(dpPx(16), dpPx(14), dpPx(16), dpPx(12));
        outer.addView(tvTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFD1D1D6);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        outer.addView(div);

        TextView tvQuickLabel = new TextView(this);
        tvQuickLabel.setText("Chọn nhanh");
        tvQuickLabel.setTextSize(13);
        tvQuickLabel.setTextColor(0xFF8E8E93);
        tvQuickLabel.setPadding(dpPx(20), dpPx(20), dpPx(20), dpPx(8));
        outer.addView(tvQuickLabel);

        String[] quickLabels = {"30 phút", "1 giờ", "1 giờ 30'", "3 giờ", "12 giờ", "1 ngày", "2 ngày", "3 ngày", "7 ngày"};
        long[] quickMillis = {
                30L * 60 * 1000, ONE_HOUR, (long)(1.5 * ONE_HOUR),
                3 * ONE_HOUR, 12 * ONE_HOUR,
                ONE_DAY, 2 * ONE_DAY, 3 * ONE_DAY, 7 * ONE_DAY
        };

        com.google.android.material.chip.ChipGroup chipGroup = new com.google.android.material.chip.ChipGroup(this);
        LinearLayout.LayoutParams cgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cgLp.setMargins(dpPx(16), 0, dpPx(16), 0);
        chipGroup.setLayoutParams(cgLp);
        chipGroup.setChipSpacingHorizontal(dpPx(8));
        chipGroup.setChipSpacingVertical(dpPx(8));

        for (int i = 0; i < quickLabels.length; i++) {
            final int index = i;
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setText(quickLabels[i]);
            chip.setCheckable(true);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF2F2F7));
            chip.setChipStrokeWidth(0f);
            chip.setTextColor(0xFF1C1C1E);
            chip.setChipCornerRadius(dpPx(20));
            chip.setOnClickListener(v -> {
                selectedDurationMillis = quickMillis[index];
                selectedDurationLabel = quickLabels[index];
                tvSelectedDuration.setText("⏰ Thời hạn: " + selectedDurationLabel);
                cardSelectedDuration.setVisibility(View.VISIBLE);
                dialog.dismiss();
            });
            chipGroup.addView(chip);
        }
        outer.addView(chipGroup);

        TextView tvCustomLabel = new TextView(this);
        tvCustomLabel.setText("Hoặc nhập thủ công");
        tvCustomLabel.setTextSize(13);
        tvCustomLabel.setTextColor(0xFF8E8E93);
        tvCustomLabel.setPadding(dpPx(20), dpPx(20), dpPx(20), dpPx(8));
        outer.addView(tvCustomLabel);

        com.google.android.material.card.MaterialCardView inputCard =
                new com.google.android.material.card.MaterialCardView(this);
        LinearLayout.LayoutParams cardP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardP.setMargins(dpPx(16), 0, dpPx(16), 0);
        inputCard.setLayoutParams(cardP);
        inputCard.setCardBackgroundColor(0xFFF2F2F7);
        inputCard.setRadius(dpPx(14));
        inputCard.setCardElevation(0f);
        inputCard.setStrokeWidth(0);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dpPx(20), dpPx(16), dpPx(20), dpPx(16));

        EditText etHours = new EditText(this);
        etHours.setHint("0");
        etHours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etHours.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        etHours.setBackground(null);
        etHours.setTextSize(18);
        etHours.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        inputRow.addView(etHours);

        TextView tvH = new TextView(this);
        tvH.setText(" giờ    ");
        tvH.setTextSize(16);
        tvH.setTextColor(0xFF8E8E93);
        inputRow.addView(tvH);

        EditText etMinutes = new EditText(this);
        etMinutes.setHint("0");
        etMinutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMinutes.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        etMinutes.setBackground(null);
        etMinutes.setTextSize(18);
        etMinutes.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        inputRow.addView(etMinutes);

        TextView tvM = new TextView(this);
        tvM.setText(" phút");
        tvM.setTextSize(16);
        tvM.setTextColor(0xFF8E8E93);
        inputRow.addView(tvM);

        inputCard.addView(inputRow);
        outer.addView(inputCard);

        com.google.android.material.button.MaterialButton btnConfirm =
                new com.google.android.material.button.MaterialButton(this);
        btnConfirm.setText("Xác nhận");
        btnConfirm.setTextSize(16);
        btnConfirm.setAllCaps(false);
        btnConfirm.setCornerRadius(dpPx(26));
        btnConfirm.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(52));
        btnP.setMargins(dpPx(20), dpPx(20), dpPx(20), dpPx(24));
        btnConfirm.setLayoutParams(btnP);

        btnConfirm.setOnClickListener(v -> {
            String hStr = etHours.getText().toString().trim();
            String mStr = etMinutes.getText().toString().trim();
            int hours = hStr.isEmpty() ? 0 : Integer.parseInt(hStr);
            int minutes = mStr.isEmpty() ? 0 : Integer.parseInt(mStr);
            if (hours == 0 && minutes == 0) {
                Toast.makeText(this, "Vui lòng nhập thời hạn", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedDurationMillis = hours * ONE_HOUR + minutes * 60L * 1000L;
            StringBuilder label = new StringBuilder();
            if (hours > 0) label.append(hours).append(" giờ");
            if (minutes > 0) {
                if (hours > 0) label.append(" ");
                label.append(minutes).append(" phút");
            }
            selectedDurationLabel = label.toString();
            tvSelectedDuration.setText("⏰ Thời hạn: " + selectedDurationLabel);
            cardSelectedDuration.setVisibility(View.VISIBLE);
            dialog.dismiss();
        });
        outer.addView(btnConfirm);

        dialog.setContentView(outer);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = ((BottomSheetDialog) dialogInterface)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bottomSheet.setBackground(bg);

                com.google.android.material.bottomsheet.BottomSheetBehavior behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
    }

    private void showTagDialog() {
        // Lấy sở thích đã lưu từ SharedPreferences
        android.content.SharedPreferences prefs =
                getSharedPreferences("weconnect_prefs", MODE_PRIVATE);
        String savedInterests = prefs.getString("user_interests", "");

        if (savedInterests.isEmpty()) {
            // SharedPreferences trống → thử load từ backend API
            RetrofitClient.loadToken(this);
            com.example.weconnect.api.UserApiService userApi =
                    RetrofitClient.getClient().create(com.example.weconnect.api.UserApiService.class);

            userApi.getInterests().enqueue(new retrofit2.Callback<com.example.weconnect.models.ApiResponse<java.util.List<String>>>() {
                @Override
                public void onResponse(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call,
                                       retrofit2.Response<com.example.weconnect.models.ApiResponse<java.util.List<String>>> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getResult() != null
                            && !response.body().getResult().isEmpty()) {
                        java.util.List<String> interests = response.body().getResult();
                        // Lưu vào SharedPreferences để lần sau không cần gọi API
                        prefs.edit().putString("user_interests", String.join(",", interests)).apply();
                        // Hiển thị dialog
                        showTagDialogWithInterests(interests.toArray(new String[0]));
                    } else {
                        Toast.makeText(CreatePostActivity.this,
                                "Bạn chưa chọn sở thích! Vui lòng vào trang cá nhân để cập nhật.",
                                Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<com.example.weconnect.models.ApiResponse<java.util.List<String>>> call, Throwable t) {
                    Toast.makeText(CreatePostActivity.this,
                            "Không thể tải sở thích. Vui lòng kiểm tra kết nối mạng.",
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // Đã có sở thích trong SharedPreferences
            showTagDialogWithInterests(savedInterests.split(","));
        }
    }

    private void showTagDialogWithInterests(String[] interests) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);

        // Outer: handle bar + scrollable body
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(0x00000000);

        // Handle bar
        View handle = new View(this);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dpPx(40), dpPx(4));
        handleLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dpPx(12);
        handle.setLayoutParams(handleLp);
        handle.setBackgroundColor(0xFFD1D1D6);
        outer.addView(handle);

        // Title
        TextView tvTitle = new TextView(this);
        tvTitle.setText("Sở thích");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(0xFF1C1C1E);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        tvTitle.setPadding(dpPx(16), dpPx(14), dpPx(16), dpPx(12));
        outer.addView(tvTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Divider
        View div = new View(this);
        div.setBackgroundColor(0xFFD1D1D6);
        div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        outer.addView(div);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("Chọn sở thích phù hợp cho bài viết của bạn");
        tvDesc.setTextSize(13);
        tvDesc.setTextColor(0xFF8E8E93);
        tvDesc.setGravity(android.view.Gravity.CENTER);
        tvDesc.setPadding(dpPx(20), dpPx(16), dpPx(20), dpPx(8));
        outer.addView(tvDesc);

        ChipGroup chipGroup = new ChipGroup(this);
        chipGroup.setSingleSelection(true);
        LinearLayout.LayoutParams cgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cgLp.setMargins(dpPx(16), dpPx(8), dpPx(16), 0);
        chipGroup.setLayoutParams(cgLp);
        chipGroup.setChipSpacingHorizontal(dpPx(8));
        chipGroup.setChipSpacingVertical(dpPx(8));

        for (String interest : interests) {
            String tag = interest.trim();
            if (tag.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText(tag);
            chip.setCheckable(true);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF2F2F7));
            chip.setChipStrokeWidth(0f);
            chip.setTextColor(0xFF1C1C1E);
            chip.setChipCornerRadius(dpPx(20));
            chip.setTextSize(14);
            chipGroup.addView(chip);
        }
        outer.addView(chipGroup);

        com.google.android.material.button.MaterialButton btnOk =
                new com.google.android.material.button.MaterialButton(this);
        btnOk.setText("Xác nhận");
        btnOk.setTextSize(16);
        btnOk.setAllCaps(false);
        btnOk.setCornerRadius(dpPx(26));
        btnOk.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                getResources().getColor(R.color.primary_pink, null)));
        LinearLayout.LayoutParams btnP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpPx(52));
        btnP.setMargins(dpPx(20), dpPx(20), dpPx(20), dpPx(24));
        btnOk.setLayoutParams(btnP);

        btnOk.setOnClickListener(v -> {
            int checkedId = chipGroup.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip selected = chipGroup.findViewById(checkedId);
                selectedTag = selected.getText().toString();
                tvSelectedTag.setText(selectedTag);
                cardSelectedTag.setVisibility(View.VISIBLE);
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Bạn chưa chọn sở thích nào!", Toast.LENGTH_SHORT).show();
            }
        });
        outer.addView(btnOk);

        dialog.setContentView(outer);

        dialog.setOnShowListener(dialogInterface -> {
            FrameLayout bottomSheet = ((BottomSheetDialog) dialogInterface)
                    .findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                bg.setColor(0xFFFFFFFF);
                float r = dpPx(24);
                bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
                bottomSheet.setBackground(bg);

                com.google.android.material.bottomsheet.BottomSheetBehavior behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setFitToContents(true);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        dialog.show();
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

        ImageView ivCloseLocation = view.findViewById(R.id.ivCloseLocation);
        com.google.android.material.textfield.MaterialAutoCompleteTextView actWard = view.findViewById(R.id.actWard);
        com.google.android.material.textfield.MaterialAutoCompleteTextView actDistrict = view.findViewById(R.id.actDistrict);
        com.google.android.material.textfield.MaterialAutoCompleteTextView actCity = view.findViewById(R.id.actCity);
        TextView tvLocationPreview = view.findViewById(R.id.tvLocationPreview);
        MaterialButton btnOkLocation = view.findViewById(R.id.btnOkLocation);

        ivCloseLocation.setOnClickListener(v -> dialog.dismiss());

        // Ha Noi now uses a 2-level official address: province/city -> ward/commune.
        // The old district dropdown is retained only as a UI filter and is never saved.
        java.util.LinkedHashMap<String, java.util.LinkedHashMap<String, String[]>> locationData = new java.util.LinkedHashMap<>();

        java.util.LinkedHashMap<String, String[]> hanoiDistrictFilters = new java.util.LinkedHashMap<>();
        hanoiDistrictFilters.put("Tất cả phường/xã", AdministrativeLocationData
                .hanoiWardDisplayNames(null).toArray(new String[0]));
        for (String oldDistrict : AdministrativeLocationData.hanoiOldDistrictFilters()) {
            hanoiDistrictFilters.put(oldDistrict, AdministrativeLocationData
                    .hanoiWardDisplayNames(oldDistrict).toArray(new String[0]));
        }
        locationData.put(AdministrativeLocationData.HANOI_NAME, hanoiDistrictFilters);

        // TP.HCM
        java.util.LinkedHashMap<String, String[]> hcmDistricts = new java.util.LinkedHashMap<>();
        hcmDistricts.put("Thủ Đức", new String[]{"Phường Linh Trung", "Phường Hiệp Bình Chánh", "Phường Tam Bình", "Phường Trường Thọ"});
        hcmDistricts.put("Quận 1", new String[]{"Phường Bến Nghé", "Phường Bến Thành", "Phường Đa Kao", "Phường Nguyễn Thái Bình"});
        hcmDistricts.put("Quận 7", new String[]{"Phường Tân Phong", "Phường Phú Mỹ", "Phường Tân Kiểng", "Phường Tân Hưng"});
        hcmDistricts.put("Bình Thạnh", new String[]{"Phường 1", "Phường 2", "Phường 3", "Phường 7", "Phường 11"});
        locationData.put("TP.HCM", hcmDistricts);

        // Đà Nẵng
        java.util.LinkedHashMap<String, String[]> danangDistricts = new java.util.LinkedHashMap<>();
        danangDistricts.put("Hải Châu", new String[]{"Phường Thạch Thang", "Phường Thanh Bình", "Phường Hải Châu I", "Phường Hải Châu II"});
        danangDistricts.put("Sơn Trà", new String[]{"Phường An Hải Bắc", "Phường An Hải Đông", "Phường Mân Thái", "Phường Phước Mỹ"});
        danangDistricts.put("Ngũ Hành Sơn", new String[]{"Phường Mỹ An", "Phường Khuê Mỹ", "Phường Hoà Hải", "Phường Hoà Quý"});
        locationData.put("Đà Nẵng", danangDistricts);

        // City adapter
        String[] cities = locationData.keySet().toArray(new String[0]);
        android.widget.ArrayAdapter<String> cityAdapter =
                new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, cities);
        actCity.setAdapter(cityAdapter);

        // When city selected -> update area filters and wards
        actCity.setOnItemClickListener((parent, v, position, id) -> {
            String city = actCity.getText().toString();
            actDistrict.setText("", false);
            actWard.setText("", false);

            java.util.LinkedHashMap<String, String[]> districts = locationData.get(city);
            if (districts != null) {
                String[] districtNames = districts.keySet().toArray(new String[0]);
                actDistrict.setAdapter(new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, districtNames));
                String[] wards = AdministrativeLocationData.HANOI_NAME.equals(city)
                        ? districts.get("Tất cả phường/xã")
                        : new String[]{};
                if (wards != null) {
                    actWard.setAdapter(new android.widget.ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, wards));
                }
            } else {
                actWard.setAdapter(new android.widget.ArrayAdapter<>(this,
                        android.R.layout.simple_dropdown_item_1line, new String[]{}));
            }
            updateLocationPreview(actCity, actDistrict, actWard, tvLocationPreview);
        });

        // When area filter/district selected -> update wards
        actDistrict.setOnItemClickListener((parent, v, position, id) -> {
            String city = actCity.getText().toString();
            String district = actDistrict.getText().toString();
            actWard.setText("", false);

            java.util.LinkedHashMap<String, String[]> districts = locationData.get(city);
            if (districts != null) {
                String[] wards = districts.get(district);
                if (wards != null) {
                    actWard.setAdapter(new android.widget.ArrayAdapter<>(this,
                            android.R.layout.simple_dropdown_item_1line, wards));
                }
            }
            updateLocationPreview(actCity, actDistrict, actWard, tvLocationPreview);
        });

        actWard.setOnItemClickListener((parent, v, position, id) ->
                updateLocationPreview(actCity, actDistrict, actWard, tvLocationPreview));

        // Pre-fill
        if (selectedLocation != null && selectedLocation.length() > 0) {
            tvLocationPreview.setText("📍 " + selectedLocation);
        }

        // Quick chips
        Chip chipHaDong = view.findViewById(R.id.chipHaDong);
        Chip chipCauGiay = view.findViewById(R.id.chipCauGiay);
        Chip chipThuDuc = view.findViewById(R.id.chipThuDuc);
        Chip chipHaiChau = view.findViewById(R.id.chipHaiChau);

        chipHaDong.setText("Hà Đông");
        chipCauGiay.setText("Cầu Giấy");
        chipThuDuc.setText("Thủ Đức");
        chipHaiChau.setText("Hải Châu");

        chipHaDong.setOnClickListener(v -> {
            actCity.setText("Hà Nội", false);
            actCity.getOnItemClickListener().onItemClick(null, v, 0, 0);
            actDistrict.setText("Hà Đông", false);
            actDistrict.getOnItemClickListener().onItemClick(null, v, 0, 0);
        });
        chipCauGiay.setOnClickListener(v -> {
            actCity.setText("Hà Nội", false);
            actCity.getOnItemClickListener().onItemClick(null, v, 0, 0);
            actDistrict.setText("Cầu Giấy", false);
            actDistrict.getOnItemClickListener().onItemClick(null, v, 0, 0);
        });
        chipThuDuc.setOnClickListener(v -> {
            actCity.setText("TP.HCM", false);
            actCity.getOnItemClickListener().onItemClick(null, v, 0, 0);
            actDistrict.setText("Thủ Đức", false);
            actDistrict.getOnItemClickListener().onItemClick(null, v, 0, 0);
        });
        chipHaiChau.setOnClickListener(v -> {
            actCity.setText("Đà Nẵng", false);
            actCity.getOnItemClickListener().onItemClick(null, v, 0, 0);
            actDistrict.setText("Hải Châu", false);
            actDistrict.getOnItemClickListener().onItemClick(null, v, 0, 0);
        });

        btnOkLocation.setOnClickListener(v -> {
            String ward = actWard.getText() != null ? actWard.getText().toString().trim() : "";
            String district = actDistrict.getText() != null ? actDistrict.getText().toString().trim() : "";
            String city = actCity.getText() != null ? actCity.getText().toString().trim() : "";

            if (ward.length() == 0 && district.length() == 0 && city.length() == 0) {
                Toast.makeText(this, "Bạn chưa chọn địa điểm!", Toast.LENGTH_SHORT).show();
                return;
            }

            if (AdministrativeLocationData.HANOI_NAME.equals(city)) {
                if (ward.length() == 0) {
                    Toast.makeText(this, "Vui lòng chọn phường/xã mới của Hà Nội.", Toast.LENGTH_SHORT).show();
                    return;
                }
                selectedLocation = ward + ", " + AdministrativeLocationData.HANOI_NAME;
            } else {
                StringBuilder builder = new StringBuilder();
                boolean hasValue = false;
                if (ward.length() > 0) { builder.append(ward); hasValue = true; }
                if (district.length() > 0) { if (hasValue) builder.append(", "); builder.append(district); hasValue = true; }
                if (city.length() > 0) { if (hasValue) builder.append(", "); builder.append(city); }
                selectedLocation = builder.toString();
            }

            tvSelectedLocation.setText("📍 " + selectedLocation);
            cardSelectedLocation.setVisibility(View.VISIBLE);
            dialog.dismiss();
        });

        dialog.show();
    }

    private int dpPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void updateLocationPreview(
            com.google.android.material.textfield.MaterialAutoCompleteTextView actCity,
            com.google.android.material.textfield.MaterialAutoCompleteTextView actDistrict,
            com.google.android.material.textfield.MaterialAutoCompleteTextView actWard,
            TextView tvPreview) {
        String ward = actWard.getText() != null ? actWard.getText().toString().trim() : "";
        String district = actDistrict.getText() != null ? actDistrict.getText().toString().trim() : "";
        String city = actCity.getText() != null ? actCity.getText().toString().trim() : "";

        if (AdministrativeLocationData.HANOI_NAME.equals(city)) {
            if (ward.length() > 0) {
                tvPreview.setText("📍 " + ward + ", " + AdministrativeLocationData.HANOI_NAME);
            } else if (district.length() > 0) {
                tvPreview.setText("📍 Bộ lọc khu vực: " + district + " · Chọn phường/xã để lưu địa chỉ");
            } else {
                tvPreview.setText("📍 Hà Nội · Chọn phường/xã mới");
            }
            return;
        }

        StringBuilder builder = new StringBuilder("📍 ");
        boolean hasValue = false;
        if (ward.length() > 0) { builder.append(ward); hasValue = true; }
        if (district.length() > 0) { if (hasValue) builder.append(", "); builder.append(district); hasValue = true; }
        if (city.length() > 0) { if (hasValue) builder.append(", "); builder.append(city); hasValue = true; }

        if (!hasValue) {
            tvPreview.setText("📍 Chưa chọn địa điểm");
        } else {
            tvPreview.setText(builder.toString());
        }
    }
}
