package com.example.weconnect.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.R;
import com.example.weconnect.api.ReportApiService;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.api.RetrofitClient;
import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportPenaltyDetailActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private LinearLayout layoutError;
    private TextView tvErrorMsg;
    private ScrollView scrollContent;
    private MaterialButton btnUnderstood;

    private TextView tvTargetType;
    private TextView tvReason;
    private TextView tvPenaltyPoint;
    private TextView tvCurrentScore;
    private TextView tvAdminNote;
    private TextView tvReviewedAt;
    private LinearLayout layoutAdminNote;
    private View dividerAdminNote;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_penalty_detail);

        progressBar = findViewById(R.id.progressBar);
        layoutError = findViewById(R.id.layoutError);
        tvErrorMsg = findViewById(R.id.tvErrorMsg);
        scrollContent = findViewById(R.id.scrollContent);
        btnUnderstood = findViewById(R.id.btnUnderstood);
        tvTargetType = findViewById(R.id.tvTargetType);
        tvReason = findViewById(R.id.tvReason);
        tvPenaltyPoint = findViewById(R.id.tvPenaltyPoint);
        tvCurrentScore = findViewById(R.id.tvCurrentScore);
        tvAdminNote = findViewById(R.id.tvAdminNote);
        tvReviewedAt = findViewById(R.id.tvReviewedAt);
        layoutAdminNote = findViewById(R.id.layoutAdminNote);
        dividerAdminNote = findViewById(R.id.dividerAdminNote);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        btnUnderstood.setOnClickListener(v -> finish());

        long reportId = getIntent().getLongExtra("report_id", -1);
        if (reportId <= 0) {
            showError("Không tìm thấy thông tin báo cáo.");
            return;
        }
        loadReportDetail(reportId);
    }

    private void loadReportDetail(long reportId) {
        progressBar.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        scrollContent.setVisibility(View.GONE);
        btnUnderstood.setVisibility(View.GONE);

        RetrofitClient.loadToken(this);
        ReportApiService api = RetrofitClient.getClient().create(ReportApiService.class);
        api.getMyReportDetail(reportId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    bindData(response.body().getResult());
                } else {
                    showError("Không thể tải chi tiết báo cáo. Vui lòng thử lại sau.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showError("Không thể tải chi tiết báo cáo. Vui lòng thử lại sau.");
            }
        });
    }

    private void bindData(Map<String, Object> data) {
        String targetType = data.get("targetType") != null ? data.get("targetType").toString() : "";
        tvTargetType.setText("USER".equals(targetType) ? "Báo cáo người dùng" : "Báo cáo bài viết");

        String reason = data.get("reason") != null ? data.get("reason").toString() : "—";
        tvReason.setText(reason);

        int penalty = data.get("penaltyPoint") != null
                ? ((Number) data.get("penaltyPoint")).intValue() : 0;
        tvPenaltyPoint.setText("−" + penalty + " điểm");

        if (data.get("currentReputationScore") != null) {
            int score = (int) Math.round(((Number) data.get("currentReputationScore")).doubleValue());
            tvCurrentScore.setText(score + " / 100");
        } else {
            tvCurrentScore.setText("—");
        }

        String adminNote = data.get("adminAction") != null ? data.get("adminAction").toString().trim() : "";
        if (!adminNote.isEmpty()) {
            tvAdminNote.setText(adminNote);
            layoutAdminNote.setVisibility(View.VISIBLE);
            dividerAdminNote.setVisibility(View.VISIBLE);
        }

        String reviewedAt = data.get("reviewedAt") != null ? data.get("reviewedAt").toString() : null;
        tvReviewedAt.setText(formatDateTime(reviewedAt));

        scrollContent.setVisibility(View.VISIBLE);
        btnUnderstood.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        tvErrorMsg.setText(message);
        layoutError.setVisibility(View.VISIBLE);
        scrollContent.setVisibility(View.GONE);
        btnUnderstood.setVisibility(View.GONE);
    }

    private String formatDateTime(String raw) {
        if (raw == null || raw.isEmpty()) return "—";
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = in.parse(raw);
            SimpleDateFormat out = new SimpleDateFormat("HH:mm, dd/MM/yyyy", Locale.getDefault());
            return out.format(date);
        } catch (ParseException e) {
            return raw;
        }
    }
}
