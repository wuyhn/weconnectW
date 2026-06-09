package com.weconnect.backend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class FCMService {

    public void sendNotification(String fcmToken, String title, String body, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build());
            if (data != null) {
                builder.putAllData(data);
            }
            FirebaseMessaging.getInstance().sendAsync(builder.build());
        } catch (Exception e) {
            // Firebase not configured or token invalid — skip silently
        }
    }

    /**
     * Gửi FCM Data-only message — KHÔNG có notification block.
     * Dùng cho Kịch bản 1 (FORCE_LOGOUT): khi app đang foreground, Android sẽ gọi
     * onMessageReceived() thay vì hiển thị notification tray, cho phép xử lý logic
     * ngay lập tức mà không làm phiền user bằng thông báo thừa.
     *
     * Lưu ý: khi app ở background và không có notification block, FCM sẽ vẫn
     * giao message vào onMessageReceived() (khác với notification message bị hệ thống
     * tự hiển thị). Điều này đảm bảo FORCE_LOGOUT luôn được xử lý đúng cách.
     */
    public void sendDataOnlyMessage(String fcmToken, Map<String, String> data) {
        if (fcmToken == null || fcmToken.isBlank()) return;
        try {
            Message.Builder builder = Message.builder()
                    .setToken(fcmToken)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build());
            if (data != null) {
                builder.putAllData(data);
            }
            FirebaseMessaging.getInstance().sendAsync(builder.build());
        } catch (Exception e) {
            // Firebase not configured or token invalid — skip silently
        }
    }
}
