package com.example.weconnect.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.websocket.WebSocketManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity trong suốt, chỉ hiển thị AlertDialog thông báo tài khoản bị khóa.
 * Được start từ:
 *   - MainActivity (WebSocket real-time khi user đang online)
 *   - WeConnectMessagingService (FCM fallback khi app background)
 *
 * Design: không có layout, cửa sổ trong suốt, chỉ thấy dialog.
 * Người dùng KHÔNG thể tắt dialog bằng cách bấm ra ngoài hoặc nhấn Back.
 * Sau khi bấm "Xác nhận" → xóa session → về LoginActivity với stack sạch.
 */
public class ForceLogoutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Đặt nền Activity trong suốt để chỉ thấy dialog, không thấy Activity background
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        String rawMessage  = getIntent().getStringExtra("lock_message");
        String lockUntilIso = getIntent().getStringExtra("lock_until");

        String displayMessage = buildDisplayMessage(rawMessage, lockUntilIso);
        showBlockDialog(displayMessage);
    }

    /**
     * Xây dựng nội dung dialog đầy đủ gồm lý do khóa và ngày mở khóa.
     * lockUntilIso có dạng "2026-06-06T10:30:00" (ISO-8601, do backend gửi).
     */
    private String buildDisplayMessage(String rawMessage, String lockUntilIso) {
        StringBuilder sb = new StringBuilder();

        // Nội dung vi phạm
        if (rawMessage != null && !rawMessage.isEmpty()) {
            sb.append(rawMessage);
        } else {
            sb.append("Tài khoản của bạn đã bị khóa tạm thời 7 ngày do vi phạm tiêu chuẩn cộng đồng!");
        }

        // Ngày mở khóa — parse ISO-8601 → định dạng dd/MM/yyyy
        if (lockUntilIso != null && !lockUntilIso.isEmpty()) {
            try {
                SimpleDateFormat inFmt  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                SimpleDateFormat outFmt = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                Date date = inFmt.parse(lockUntilIso);
                if (date != null) {
                    sb.append("\n\nVui lòng quay lại vào ngày ").append(outFmt.format(date)).append(".");
                }
            } catch (Exception ignored) {
                // Nếu parse thất bại, không thêm ngày — message vẫn có nội dung vi phạm
            }
        }

        return sb.toString();
    }

    /**
     * Hiển thị AlertDialog dùng AppCompat builder — đảm bảo tương thích với mọi theme.
     * setCancelable(false) + override onBackPressed() = user bắt buộc bấm "Xác nhận".
     */
    private void showBlockDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Tài khoản bị khóa")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Xác nhận", (dialog, which) -> doLogout())
                .show();
    }

    /** Xóa toàn bộ session và đẩy về LoginActivity — xóa sạch back stack. */
    private void doLogout() {
        WebSocketManager.getInstance().disconnect();
        RetrofitClient.clearSession(this);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Chặn nút Back — bắt buộc bấm "Xác nhận"
    }
}
